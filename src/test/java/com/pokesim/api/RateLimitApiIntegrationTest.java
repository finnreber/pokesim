package com.pokesim.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Limits are tiny here so they are easy to hit. Each test uses its own client IP so tests cannot affect each other. */
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.auth.requests=3", "app.rate-limit.auth.per-seconds=60",
        "app.rate-limit.simulate.requests=2", "app.rate-limit.simulate.per-seconds=60",
        "app.rate-limit.api.requests=1000", "app.rate-limit.api.per-seconds=60"})
class RateLimitApiIntegrationTest extends IntegrationTestBase {

    private static RequestPostProcessor clientIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test @DisplayName("the public simulation allows a small burst, then answers 429 with Retry-After")
    void simulationIsLimited() throws Exception {
        RequestPostProcessor ip = clientIp("10.9.0.1");
        mvc.perform(post("/api/v1/simulate").with(ip).contentType(JSON).content("{\"seed\":1,\"teamSize\":1}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/simulate").with(ip).contentType(JSON).content("{\"seed\":1,\"teamSize\":1}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/simulate").with(ip).contentType(JSON).content("{\"seed\":1,\"teamSize\":1}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.title").value("Too many requests"));
    }

    @Test @DisplayName("limits are per client: one noisy client does not block another")
    void clientsAreIndependent() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/simulate").with(clientIp("10.9.1.1")).contentType(JSON).content("{\"teamSize\":1}"));
        }
        mvc.perform(post("/api/v1/simulate").with(clientIp("10.9.1.1")).contentType(JSON).content("{\"teamSize\":1}"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/v1/simulate").with(clientIp("10.9.1.2")).contentType(JSON).content("{\"teamSize\":1}"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("login attempts are limited even when every attempt fails, which slows password guessing")
    void loginIsLimited() throws Exception {
        RequestPostProcessor ip = clientIp("10.9.2.1");
        String bad = credentials(randomUsername(), "wrong-password");
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/login").with(ip).contentType(JSON).content(bad)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").with(ip).contentType(JSON).content(bad)).andExpect(status().isTooManyRequests());
    }

    @Test @DisplayName("the budgets are separate: using up login attempts does not block reading the dex")
    void budgetsAreSeparate() throws Exception {
        RequestPostProcessor ip = clientIp("10.9.3.1");
        String bad = credentials(randomUsername(), "wrong-password");
        for (int i = 0; i < 4; i++) mvc.perform(post("/api/v1/auth/login").with(ip).contentType(JSON).content(bad));
        mvc.perform(get("/api/v1/species/25").with(ip)).andExpect(status().isOk());
    }

    @Test @DisplayName("health checks are never limited (Render pings them constantly)")
    void healthIsExempt() throws Exception {
        RequestPostProcessor ip = clientIp("10.9.4.1");
        for (int i = 0; i < 20; i++) mvc.perform(get("/actuator/health").with(ip)).andExpect(status().isOk());
    }
}
