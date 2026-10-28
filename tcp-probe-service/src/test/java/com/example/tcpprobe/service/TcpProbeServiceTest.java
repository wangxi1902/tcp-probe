package com.example.tcpprobe.service;

import com.example.tcpprobe.config.ProbeProperties;
import com.example.tcpprobe.probe.ProbeResult;
import com.example.tcpprobe.probe.ProbeStatus;
import com.example.tcpprobe.probe.TcpProbeClient;
import com.example.tcpprobe.ratelimit.TokenBucketRateLimiter;
import com.example.tcpprobe.web.dto.BatchProbeRequest;
import com.example.tcpprobe.web.dto.BatchProbeResponse;
import com.example.tcpprobe.web.dto.ProbeTargetRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TcpProbeServiceTest {

    private final TcpProbeClient probeClient = mock(TcpProbeClient.class);
    private final TokenBucketRateLimiter rateLimiter = mock(TokenBucketRateLimiter.class);
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2,
            2,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(10)
    );
    private final ProbeProperties properties = new ProbeProperties();
    private final TcpProbeService service = new TcpProbeService(
            probeClient,
            rateLimiter,
            executor,
            properties
    );

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @Test
    void shouldSeparateFailedNotExecutedAndInternalErrorResults() {
        ProbeTargetRequest success = new ProbeTargetRequest("success", "success.test", 80);
        ProbeTargetRequest refused = new ProbeTargetRequest("refused", "refused.test", 81);
        ProbeTargetRequest limited = new ProbeTargetRequest("limited", "limited.test", 82);
        ProbeTargetRequest broken = new ProbeTargetRequest("broken", "broken.test", 83);

        // 第三个目标拿不到令牌；其他目标会进入线程池执行。
        when(rateLimiter.tryAcquire()).thenReturn(true, true, false, true);
        when(probeClient.probe("success", "success.test", 80, 3000))
                .thenReturn(ProbeResult.success("success", "success.test", 80, 3));
        when(probeClient.probe("refused", "refused.test", 81, 3000))
                .thenReturn(ProbeResult.failure(
                        "refused",
                        "refused.test",
                        81,
                        ProbeStatus.CONNECTION_REFUSED,
                        2L,
                        "目标拒绝连接"
                ));
        when(probeClient.probe("broken", "broken.test", 83, 3000))
                .thenThrow(new IllegalStateException("模拟内部错误"));

        BatchProbeResponse response = service.probeBatch(
                new BatchProbeRequest(List.of(success, refused, limited, broken), null)
        );

        assertThat(response.total()).isEqualTo(4);
        assertThat(response.reachableCount()).isEqualTo(1);
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.notExecutedCount()).isEqualTo(1);
        assertThat(response.internalErrorCount()).isEqualTo(1);
        assertThat(response.results())
                .extracting(ProbeResult::id)
                .containsExactly("success", "refused", "limited", "broken");
        assertThat(response.results().get(3).status()).isEqualTo(ProbeStatus.INTERNAL_ERROR);
        assertThat(response.results().get(3).host()).isEqualTo("broken.test");
    }

    @Test
    void shouldExecuteAllowedTargetsConcurrently() throws Exception {
        ProbeTargetRequest first = new ProbeTargetRequest("first", "first.test", 80);
        ProbeTargetRequest second = new ProbeTargetRequest("second", "second.test", 81);
        CountDownLatch bothProbesStarted = new CountDownLatch(2);

        when(rateLimiter.tryAcquire()).thenReturn(true, true);
        when(probeClient.probe(anyString(), anyString(), anyInt(), eq(1000)))
                .thenAnswer(invocation -> {
                    /*
                     * 每个探测都要等另一个探测进入方法后才能返回。
                     * 如果 Service 是串行执行，第一个调用会等待超时，测试就会失败。
                     */
                    bothProbesStarted.countDown();
                    if (!bothProbesStarted.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("两个探测任务没有并发执行");
                    }

                    return ProbeResult.success(
                            invocation.getArgument(0),
                            invocation.getArgument(1),
                            invocation.getArgument(2),
                            1
                    );
                });

        BatchProbeResponse response = service.probeBatch(
                new BatchProbeRequest(List.of(first, second), 1000)
        );

        assertThat(response.reachableCount()).isEqualTo(2);
        assertThat(response.results())
                .extracting(ProbeResult::id)
                .containsExactly("first", "second");
    }

    @Test
    void shouldReturnServerBusyWhenExecutorQueueIsFull() throws Exception {
        ThreadPoolExecutor saturatedExecutor = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                new ThreadPoolExecutor.AbortPolicy()
        );
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);

        try {
            // 第一个任务占用唯一工作线程，第二个任务占用唯一队列位置。
            saturatedExecutor.execute(() -> {
                workerStarted.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(workerStarted.await(1, TimeUnit.SECONDS)).isTrue();
            saturatedExecutor.execute(() -> {
                // 这个任务只用于占满队列，测试结束后会被 shutdownNow 清理。
            });

            TokenBucketRateLimiter allowedRateLimiter = mock(TokenBucketRateLimiter.class);
            when(allowedRateLimiter.tryAcquire()).thenReturn(true);
            TcpProbeService saturatedService = new TcpProbeService(
                    probeClient,
                    allowedRateLimiter,
                    saturatedExecutor,
                    properties
            );

            BatchProbeResponse response = saturatedService.probeBatch(
                    new BatchProbeRequest(
                            List.of(new ProbeTargetRequest("busy", "127.0.0.1", 8080)),
                            1000
                    )
            );

            assertThat(response.notExecutedCount()).isEqualTo(1);
            assertThat(response.results().get(0).status()).isEqualTo(ProbeStatus.SERVER_BUSY);
            assertThat(response.results().get(0).connectLatencyMs()).isNull();
        } finally {
            releaseWorker.countDown();
            saturatedExecutor.shutdownNow();
        }
    }
}
