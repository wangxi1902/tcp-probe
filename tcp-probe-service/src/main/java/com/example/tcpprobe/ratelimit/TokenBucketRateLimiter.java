package com.example.tcpprobe.ratelimit;

import com.example.tcpprobe.config.ProbeProperties;
import org.springframework.stereotype.Component;

/**
 * 一个进程内的简单令牌桶限流器。
 *
 * 令牌按照 permitsPerSecond 的速度补充，每次发起 TCP 探测前消耗一个令牌。
 * burstCapacity 决定服务在短时间内允许的最大突发量。没有令牌时立即拒绝，
 * 避免探测工作线程睡眠等待。
 */
@Component
public class TokenBucketRateLimiter {

    /** 每秒补充的令牌数量，决定长期平均允许速率。 */
    private final double permitsPerSecond;

    /** 桶的最大容量，决定空闲后允许的瞬时突发量。 */
    private final double capacity;

    /** 当前可用令牌数；使用小数可以按实际时间连续补充令牌。 */
    private double availableTokens;

    /** 上一次补充令牌时的单调时钟时间。 */
    private long lastRefillNanos;

    /**
     * 根据服务配置创建令牌桶，并以满桶状态启动。
     *
     * @param properties 令牌补充速率和桶容量配置
     */
    public TokenBucketRateLimiter(ProbeProperties properties) {
        this.permitsPerSecond = properties.getPermitsPerSecond();
        this.capacity = properties.getBurstCapacity();
        this.availableTokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /**
     * 尝试立即获取一个探测令牌。
     *
     * 方法使用同步锁保证“计算补充量、判断余额、扣减令牌”不可被并发线程交叉执行。
     * 没有令牌时立即返回，不阻塞也不让工作线程睡眠。
     *
     * @return 获取成功返回 {@code true}，当前速率超限返回 {@code false}
     */
    public synchronized boolean tryAcquire() {
        refillTokens();
        if (availableTokens < 1.0D) {
            return false;
        }

        availableTokens -= 1.0D;
        return true;
    }

    /** 根据距离上次调用经过的真实时间补充令牌，且不超过桶容量。 */
    private void refillTokens() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillNanos;
        if (elapsedNanos <= 0L) {
            return;
        }

        double newTokens = elapsedNanos / 1_000_000_000D * permitsPerSecond;
        availableTokens = Math.min(capacity, availableTokens + newTokens);
        lastRefillNanos = now;
    }
}
