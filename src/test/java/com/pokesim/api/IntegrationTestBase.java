package com.pokesim.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared setup for full-stack tests: ONE PostgreSQL container for the whole test run (started once, removed by
 * Testcontainers' Ryuk helper when the JVM exits) and one cached Spring context, so adding test classes stays fast.
 * Requires Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class IntegrationTestBase {
    static final MediaType JSON = MediaType.APPLICATION_JSON;
    static final String PASSWORD = "correct-horse-battery";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mvc;

    /** Unique per call, so tests never collide on usernames. Lowercase letters and digits, 13 chars. */
    static String randomUsername() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    static String credentials(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Registers a brand-new user, logs in, and returns the access token. */
    protected String registerAndLogin() throws Exception {
        String username = randomUsername();
        mvc.perform(post("/api/v1/auth/register").contentType(JSON).content(credentials(username, PASSWORD)))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/auth/login").contentType(JSON).content(credentials(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
