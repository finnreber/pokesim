package com.pokesim.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from app.rate-limit.* in application.yml. Each policy means "N requests per perSeconds, per client IP". */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, Policy auth, Policy simulate, Policy api) {
    public record Policy(int requests, int perSeconds) { }
}
