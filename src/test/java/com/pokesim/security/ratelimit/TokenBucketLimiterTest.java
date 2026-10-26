package com.pokesim.security.ratelimit;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenBucketLimiterTest {
    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);   // the fake clock: tests move time by hand

    private void advanceSeconds(long seconds) { nanos.addAndGet(seconds * 1_000_000_000L); }

    private TokenBucketLimiter limiter(int capacity, int windowSeconds) {
        return new TokenBucketLimiter(capacity, Duration.ofSeconds(windowSeconds), nanos::get);
    }

    @Test @DisplayName("a burst up to the capacity is allowed, the next request is rejected")
    void burstThenBlock() {
        TokenBucketLimiter l = limiter(3, 60);
        assertTrue(l.tryAcquire("a").allowed());
        assertTrue(l.tryAcquire("a").allowed());
        assertTrue(l.tryAcquire("a").allowed());
        assertFalse(l.tryAcquire("a").allowed());
    }

    @Test @DisplayName("the rejection says how long to wait: 10 per minute refills one token every 6 seconds")
    void retryAfter() {
        TokenBucketLimiter l = limiter(10, 60);
        for (int i = 0; i < 10; i++) assertTrue(l.tryAcquire("a").allowed());
        TokenBucketLimiter.Decision denied = l.tryAcquire("a");
        assertFalse(denied.allowed());
        assertEquals(6, denied.retryAfterSeconds());
    }

    @Test @DisplayName("tokens come back with time, one at a time")
    void refill() {
        TokenBucketLimiter l = limiter(10, 60);
        for (int i = 0; i < 10; i++) l.tryAcquire("a");
        assertFalse(l.tryAcquire("a").allowed());
        advanceSeconds(5);
        assertFalse(l.tryAcquire("a").allowed());     // 5s is not enough for a 6s token
        advanceSeconds(1);
        assertTrue(l.tryAcquire("a").allowed());      // 6s total: exactly one token
        assertFalse(l.tryAcquire("a").allowed());
    }

    @Test @DisplayName("a long idle period never banks more than the capacity")
    void neverExceedsCapacity() {
        TokenBucketLimiter l = limiter(5, 60);
        advanceSeconds(3600);
        int allowed = 0;
        for (int i = 0; i < 20; i++) if (l.tryAcquire("a").allowed()) allowed++;
        assertEquals(5, allowed);
    }

    @Test @DisplayName("each client has its own bucket")
    void keysAreIndependent() {
        TokenBucketLimiter l = limiter(1, 60);
        assertTrue(l.tryAcquire("alice").allowed());
        assertFalse(l.tryAcquire("alice").allowed());
        assertTrue(l.tryAcquire("bob").allowed());
    }

    @Test @DisplayName("idle buckets are forgotten so memory does not grow without bound")
    void idleBucketsAreEvicted() {
        TokenBucketLimiter l = limiter(5, 60);
        for (int i = 0; i < 100; i++) l.tryAcquire("client-" + i);
        assertEquals(100, l.trackedKeys());
        advanceSeconds(61);
        l.evictIdle(nanos.get());
        assertEquals(0, l.trackedKeys());
        assertTrue(l.tryAcquire("client-0").allowed());   // a fresh, full bucket
    }

    @Test @DisplayName("a bucket that is still recovering is NOT evicted (that would hand back free tokens)")
    void activeBucketsSurviveEviction() {
        TokenBucketLimiter l = limiter(2, 60);
        l.tryAcquire("a");
        l.tryAcquire("a");
        advanceSeconds(30);
        l.evictIdle(nanos.get());
        assertEquals(1, l.trackedKeys());
        assertFalse(l.tryAcquire("a").allowed() && l.tryAcquire("a").allowed());
    }

    @Test @DisplayName("under heavy concurrency exactly `capacity` requests get through")
    void threadSafe() throws Exception {
        TokenBucketLimiter l = limiter(100, 60);
        AtomicInteger allowed = new AtomicInteger();
        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++) {
            threads[t] = new Thread(() -> {
                for (int i = 0; i < 500; i++) if (l.tryAcquire("shared").allowed()) allowed.incrementAndGet();
            });
            threads[t].start();
        }
        for (Thread t : threads) t.join();
        assertEquals(100, allowed.get());
    }

    @Test @DisplayName("invalid settings are rejected")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> limiter(0, 60));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketLimiter(5, Duration.ZERO, nanos::get));
    }
}
