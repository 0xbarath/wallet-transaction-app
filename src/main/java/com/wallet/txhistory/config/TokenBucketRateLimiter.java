package com.wallet.txhistory.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class TokenBucketRateLimiter {

    private static final long TTL_NANOS = TimeUnit.HOURS.toNanos(1);

    private final ConcurrentHashMap<UUID, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;

    public TokenBucketRateLimiter(RateLimitProperties properties) {
        this.properties = properties;
    }

    public boolean tryConsume(UUID walletId) {
        TokenBucket bucket = buckets.computeIfAbsent(walletId,
                id -> new TokenBucket(properties.capacity(), properties.tokensPerSecond()));
        return bucket.tryConsume();
    }

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    void evictStaleEntries() {
        long now = System.nanoTime();
        buckets.entrySet().removeIf(e -> (now - e.getValue().lastAccessNanos) > TTL_NANOS);
    }

    private static class TokenBucket {
        private final int capacity;
        private final double refillRate;
        private double tokens;
        private long lastRefillNanos;
        volatile long lastAccessNanos;

        TokenBucket(int capacity, double refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = capacity;
            this.lastRefillNanos = System.nanoTime();
            this.lastAccessNanos = this.lastRefillNanos;
        }

        synchronized boolean tryConsume() {
            refill();
            lastAccessNanos = lastRefillNanos;
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsed = (now - lastRefillNanos) / 1_000_000_000.0;
            tokens = Math.min(capacity, tokens + elapsed * refillRate);
            lastRefillNanos = now;
        }
    }
}
