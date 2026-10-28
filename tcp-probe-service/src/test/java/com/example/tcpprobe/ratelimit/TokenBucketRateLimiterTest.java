package com.example.tcpprobe.ratelimit;

import com.example.tcpprobe.config.ProbeProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest {

    @Test
    void shouldRejectImmediatelyWhenBucketHasNoToken() {
        ProbeProperties properties = new ProbeProperties();
        properties.setPermitsPerSecond(1);
        properties.setBurstCapacity(1);
        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(properties);

        assertThat(rateLimiter.tryAcquire()).isTrue();
        assertThat(rateLimiter.tryAcquire()).isFalse();
    }
}
