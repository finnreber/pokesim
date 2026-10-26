package com.pokesim.security.ratelimit;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Token-bucket rate limiter, one bucket per key (e.g. per client IP), written in its GCRA form.
 *
 * Behaviour: a bucket holds up to {@code capacity} tokens and refills steadily over {@code window}. A request costs
 * one token. "10 per minute" lets a client send 10 at once; the 11th must wait about 6 seconds for the next token.
 *
 * Implementation: instead of storing a fractional token count (floating point drift made "retry in 6s" fail at
 * exactly 6s in an earlier version), each bucket stores one integer: the time at which it would be completely
 * empty again ("theoretical arrival time", tat). Every request pushes tat forward by one interval (window/capacity).
 * A request is allowed while tat is at most (capacity-1) intervals in the future. Pure integer math, so exact.
 *
 * The clock is injected so tests can move time without sleeping. State is in memory: each server instance counts
 * separately, which is fine for one instance; several instances would need a shared store such as Redis.
 */
public final class TokenBucketLimiter {

    public record Decision(boolean allowed, long retryAfterSeconds) {
        static final Decision ALLOWED = new Decision(true, 0);
    }

    private static final class Bucket {
        long tat;   // when this bucket would be fully refilled

        Bucket(long tat) { this.tat = tat; }
    }

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int SWEEP_EVERY_CALLS = 1024;

    private final long intervalNanos;      // time to earn one token
    private final long burstNanos;         // how far ahead of "now" tat may be while still allowing a request
    private final LongSupplier nanoClock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    public TokenBucketLimiter(int capacity, Duration window, LongSupplier nanoClock) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        if (window.isZero() || window.isNegative()) throw new IllegalArgumentException("window must be positive");
        this.intervalNanos = Math.max(1, window.toNanos() / capacity);
        this.burstNanos = intervalNanos * (capacity - 1L);
        this.nanoClock = nanoClock;
    }

    public TokenBucketLimiter(int capacity, Duration window) {
        this(capacity, window, System::nanoTime);
    }

    public Decision tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        if (calls.incrementAndGet() % SWEEP_EVERY_CALLS == 0) evictIdle(now);

        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(now));
        synchronized (bucket) {                        // one bucket's read-modify-write must be atomic
            long tat = Math.max(bucket.tat, now);      // a bucket that has fully refilled starts from "now"
            long ahead = tat - now;
            if (ahead > burstNanos) {
                long waitNanos = ahead - burstNanos;
                long seconds = (waitNanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND;   // round up
                return new Decision(false, Math.max(1, seconds));
            }
            bucket.tat = tat + intervalNanos;
            return Decision.ALLOWED;
        }
    }

    /**
     * A bucket whose tat is already in the past is full again, so forgetting it loses nothing. Without this the map
     * would grow forever with every distinct client that ever called.
     */
    void evictIdle(long now) {
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Bucket b = it.next().getValue();
            synchronized (b) {
                if (b.tat <= now) it.remove();
            }
        }
    }

    int trackedKeys() {
        return buckets.size();
    }
}
