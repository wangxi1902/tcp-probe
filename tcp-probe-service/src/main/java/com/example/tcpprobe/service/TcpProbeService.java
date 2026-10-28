package com.example.tcpprobe.service;

import com.example.tcpprobe.config.ProbeProperties;
import com.example.tcpprobe.probe.ProbeResult;
import com.example.tcpprobe.probe.ProbeStatus;
import com.example.tcpprobe.probe.TcpProbeClient;
import com.example.tcpprobe.ratelimit.TokenBucketRateLimiter;
import com.example.tcpprobe.web.dto.BatchProbeRequest;
import com.example.tcpprobe.web.dto.BatchProbeResponse;
import com.example.tcpprobe.web.dto.ProbeTargetRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 批量 TCP 探测的应用服务。
 *
 * 本类负责限流检查、并发任务提交、结果顺序保持和分类汇总。
 * 真正的单目标 Socket 连接由 {@link TcpProbeClient} 完成。
 */
@Service
public class TcpProbeService {

    private static final Logger log = LoggerFactory.getLogger(TcpProbeService.class);

    /** 执行单个目标 Socket 连接的客户端。 */
    private final TcpProbeClient probeClient;

    /** 控制单位时间内允许进入的探测任务数量。 */
    private final TokenBucketRateLimiter rateLimiter;

    /** 执行阻塞式 Socket 连接的专用有界线程池。 */
    private final ThreadPoolExecutor probeExecutor;

    /** 探测超时、线程数和限流参数。 */
    private final ProbeProperties properties;

    /**
     * 创建批量探测服务。
     *
     * @param probeClient 单目标 TCP 连接客户端
     * @param rateLimiter 进程内探测速率限制器
     * @param probeExecutor 执行阻塞式 Socket 连接的专用线程池
     * @param properties 探测服务配置
     */
    public TcpProbeService(
            TcpProbeClient probeClient,
            TokenBucketRateLimiter rateLimiter,
            ThreadPoolExecutor probeExecutor,
            ProbeProperties properties
    ) {
        this.probeClient = probeClient;
        this.rateLimiter = rateLimiter;
        this.probeExecutor = probeExecutor;
        this.properties = properties;
    }

    /**
     * 并发执行一个批次内的 TCP 探测并汇总结果。
     *
     * 循环阶段先把所有允许执行的目标提交到线程池，随后才统一
     * {@link CompletableFuture#join()}。因此任务会由多个工作线程并发执行。
     * {@code join} 只是按照原请求顺序等待和读取结果，并不会把探测退化为串行。
     *
     * @param request 已通过 HTTP 参数校验的批量请求
     * @return 包含逐目标结果和分类计数的批量响应
     */
    public BatchProbeResponse probeBatch(BatchProbeRequest request) {
        long batchStartNanos = System.nanoTime();
        int timeoutMs = request.timeoutMs() == null
                ? properties.getDefaultTimeoutMs()
                : request.timeoutMs();

        List<CompletableFuture<ProbeResult>> futures = new ArrayList<>(request.targets().size());
        for (ProbeTargetRequest target : request.targets()) {
            futures.add(submitProbe(target, timeoutMs));
        }

        /*
         * futures 的顺序与请求 targets 相同，因此逐个 join 后响应仍保持原始顺序。
         * 某一个目标失败会被转换为自己的 ProbeResult，不会让整个批次失败。
         */
        List<ProbeResult> results = futures.stream().map(CompletableFuture::join).toList();

        long reachableCount = results.stream().filter(ProbeResult::reachable).count();
        long notExecutedCount = results.stream()
                .filter(result -> result.status() == ProbeStatus.RATE_LIMITED
                        || result.status() == ProbeStatus.SERVER_BUSY)
                .count();
        long internalErrorCount = results.stream()
                .filter(result -> result.status() == ProbeStatus.INTERNAL_ERROR)
                .count();
        long failedCount = results.size() - reachableCount - notExecutedCount - internalErrorCount;
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - batchStartNanos);

        return new BatchProbeResponse(
                results.size(),
                reachableCount,
                failedCount,
                notExecutedCount,
                internalErrorCount,
                elapsedMs,
                results
        );
    }

    /**
     * 对单个目标执行限流检查，并将允许执行的任务异步提交到专用线程池。
     *
     * @param target 单个探测目标
     * @param timeoutMs 本次连接超时，单位为毫秒
     * @return 最终产生探测结果的 Future；限流或拒绝时返回已完成的 Future
     */
    private CompletableFuture<ProbeResult> submitProbe(ProbeTargetRequest target, int timeoutMs) {
        try {
            /*
             * 先获取令牌，再占用线程池。没有令牌就立即返回，避免工作线程只等待不工作。
             */
            if (!rateLimiter.tryAcquire()) {
                return CompletableFuture.completedFuture(
                        failure(target, ProbeStatus.RATE_LIMITED, "探测速率超过限制，请稍后重试")
                );
            }

            // supplyAsync 指定 probeExecutor 后，Socket 连接在 tcp-probe-* 工作线程中并发执行。
            return CompletableFuture
                    .supplyAsync(
                            () -> probeClient.probe(target.id(), target.host(), target.port(), timeoutMs),
                            probeExecutor
                    )
                    .exceptionally(exception -> {
                        // 对外隐藏异常细节，但服务端必须保留堆栈，便于定位程序问题。
                        log.error("执行 TCP 探测时发生内部错误，targetId={}, host={}, port={}",
                                target.id(), target.host(), target.port(), exception);
                        return failure(target, ProbeStatus.INTERNAL_ERROR, "执行探测任务时发生内部错误");
                    });
        } catch (RejectedExecutionException exception) {
            // 线程池队列已满时快速失败，防止无限堆积任务。
            return CompletableFuture.completedFuture(
                    failure(target, ProbeStatus.SERVER_BUSY, "探测服务繁忙，请稍后重试")
            );
        }
    }

    /**
     * 将未执行或内部异常统一转换为保留原目标信息的失败结果。
     */
    private ProbeResult failure(ProbeTargetRequest target, ProbeStatus status, String message) {
        return ProbeResult.failure(
                target.id(),
                target.host(),
                target.port(),
                status,
                null,
                message
        );
    }
}
