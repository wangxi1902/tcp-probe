package com.example.tcpprobe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 探测服务的集中配置。
 *
 * 把并发数、队列容量和限流参数放到配置文件中，避免把运行参数写死在业务代码里。
 */
@ConfigurationProperties(prefix = "probe")
@Validated
public class ProbeProperties {

    /** 请求未指定时使用的单目标连接超时，单位为毫秒。 */
    @Min(100)
    @Max(10000)
    private int defaultTimeoutMs = 3000;

    /** 同时执行阻塞式 Socket 连接的最大工作线程数。 */
    @Min(1)
    @Max(500)
    private int workerThreads = 20;

    /** 所有工作线程繁忙时允许等待执行的任务数量。 */
    @Min(1)
    @Max(10000)
    private int queueCapacity = 200;

    /** 令牌桶每秒补充的探测许可数量。 */
    @Min(1)
    @Max(10000)
    private int permitsPerSecond = 50;

    /** 令牌桶最多保存的令牌数量，即允许的最大短时突发量。 */
    @Min(1)
    @Max(10000)
    private int burstCapacity = 50;

    /**
     * 获取默认连接超时时间。
     *
     * @return 默认连接超时，单位为毫秒
     */
    public int getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    /**
     * 设置默认连接超时时间，由 Spring Boot 配置绑定调用。
     *
     * @param defaultTimeoutMs 默认连接超时，单位为毫秒
     */
    public void setDefaultTimeoutMs(int defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    /**
     * 获取探测工作线程数。
     *
     * @return 同时执行 Socket 连接的工作线程数
     */
    public int getWorkerThreads() {
        return workerThreads;
    }

    /**
     * 设置探测工作线程数，由 Spring Boot 配置绑定调用。
     *
     * @param workerThreads 探测工作线程数
     */
    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    /**
     * 获取线程池等待队列容量。
     *
     * @return 等待执行的任务上限
     */
    public int getQueueCapacity() {
        return queueCapacity;
    }

    /**
     * 设置线程池等待队列容量，由 Spring Boot 配置绑定调用。
     *
     * @param queueCapacity 等待执行的任务上限
     */
    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    /**
     * 获取令牌补充速率。
     *
     * @return 每秒补充的令牌数
     */
    public int getPermitsPerSecond() {
        return permitsPerSecond;
    }

    /**
     * 设置令牌补充速率，由 Spring Boot 配置绑定调用。
     *
     * @param permitsPerSecond 每秒补充的令牌数
     */
    public void setPermitsPerSecond(int permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
    }

    /**
     * 获取令牌桶容量。
     *
     * @return 可积累的最大令牌数
     */
    public int getBurstCapacity() {
        return burstCapacity;
    }

    /**
     * 设置令牌桶容量，由 Spring Boot 配置绑定调用。
     *
     * @param burstCapacity 可积累的最大令牌数
     */
    public void setBurstCapacity(int burstCapacity) {
        this.burstCapacity = burstCapacity;
    }

}
