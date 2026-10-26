package com.pokesim.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs before Spring Security, so floods of bad logins or bad tokens are limited too. Three separate budgets:
 * login/register (strictest: slows password guessing), the public simulation (it is CPU-heavy), and everything
 * else under /api. Clients are told apart by IP; behind Render's proxy the real client IP is restored by
 * server.forward-headers-strategy=native. /actuator and the docs are not limited.
 */
public class RateLimitFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final TokenBucketLimiter auth;
    private final TokenBucketLimiter simulate;
    private final TokenBucketLimiter api;
    private final ObjectMapper mapper;

    public RateLimitFilter(RateLimitProperties props, ObjectMapper mapper) {
        this.enabled = props.enabled();
        this.auth = limiter(props.auth());
        this.simulate = limiter(props.simulate());
        this.api = limiter(props.api());
        this.mapper = mapper;
    }

    private static TokenBucketLimiter limiter(RateLimitProperties.Policy policy) {
        return new TokenBucketLimiter(policy.requests(), Duration.ofSeconds(policy.perSeconds()));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TokenBucketLimiter.Decision decision = limiterFor(request).tryAcquire(request.getRemoteAddr());
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Try again in " + decision.retryAfterSeconds() + " second(s).");
        problem.setTitle("Too many requests");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), problem);
    }

    private TokenBucketLimiter limiterFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean post = "POST".equals(request.getMethod());
        if (post && path.startsWith("/api/v1/auth/")) return auth;
        if (post && path.equals("/api/v1/simulate")) return simulate;
        return api;
    }
}
