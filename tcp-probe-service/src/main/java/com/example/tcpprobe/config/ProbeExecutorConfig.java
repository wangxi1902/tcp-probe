package com.example.tcpprobe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 创建 TCP 探测专用线程池，隔离 HTTP 请求线程与阻塞式 Socket 任务。
 */
@Configuration
public class ProbeExecutorConfig {

    /**
     * 创建固定工作线程数、有界等待队列的探测执行器。
     *
     * @param properties 线程数和队列容量配置
     * @return 专供 TCP 探测使用的线程池
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor probeExecutor(ProbeProperties properties) {
        AtomicInteger threadNumber = new AtomicInteger(1);
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task);
            thread.setName("tcp-probe-" + threadNumber.getAndIncrement());
            return thread;
        };

        /*
         * 固定线程数限制“同时执行”的探测数量；有界队列限制“正在等待”的任务数量。
         * AbortPolicy 会在服务过载时明确拒绝新任务，避免请求无限堆积并最终拖垮 JVM。
         */
        return new ThreadPoolExecutor(
                properties.getWorkerThreads(),
                properties.getWorkerThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.getQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
