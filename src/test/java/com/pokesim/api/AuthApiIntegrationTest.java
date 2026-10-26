package com.pokesim.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.pokesim.persistence.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AuthApiIntegrationTest extends IntegrationTestBase {
    @Autowired UserRepository users;
    @Autowired JwtEncoder encoder;

    // ------------------------------------------------------------------ registration

    @Test @DisplayName("registering returns the new user, lowercases the name, and never echoes secrets")
    void register() throws Exception {
        String name = randomUsername();
        mvc.perform(post("/api/v1/auth/register").contentType(JSON).content(credentials(name.toUpperCase(), PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test @DisplayName("the password is stored as a BCrypt hash, never in plain text")
    void passwordIsHashed() throws Exception {
        String name = randomUsername();
        mvc.perform(post("/api/v1/auth/register").contentType(JSON).content(credentials(name, PASSWORD)))
                .andExpect(status().isCreated());
        String stored = users.findByUsername(name).orElseThrow().getPasswordHash();
        assertTrue(stored.startsWith("$2"), "expected a BCrypt hash but got " + stored);
        assertFalse(stored.contains(PASSWORD));
    }

    @Test @DisplayName("usernames are unique regardless of letter case")
    void duplicateUsername() throws Exception {
        String name = randomUsername();
        mvc.perform(post("/api/v1/auth/register").contentType(JSON).content(credentials(name, PASSWORD)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").contentType(JSON).content(credentials(name.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test @DisplayName("invalid registrations are 400s that name the offending fields")
    void registrationValidation() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(JSON).content(credentials("a!", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
        mvc.perform(post("/api/v1/auth/register").contentType(JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/register").contentType(JSON).content("not json"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ login

    @Test @DisplayName("login returns a signed JWT")
    void login() throws Exception {
        String name = randomUsername();
        mvc.perform(post("/api/v1/auth/register").contentType(JSON).content(credentials(name, PASSWORD)));
        String body = mvc.perform(post("/api/v1/auth/login").contentType(JSON).content(credentials(name, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");
        assertEquals(3, token.split("\\.").length);   // header.payload.signature
    }

    @Test @DisplayName("wrong password and unknown user get the identical 401, so usernames cannot be probed")
    void loginFailuresLookTheSame() throws Exception {
        String name = randomUsername();
        mvc.perform(post("/api/v1/auth/register").contentType(JSON).content(credentials(name, PASSWORD)));
        mvc.perform(post("/api/v1/auth/login").contentType(JSON).content(credentials(name, "the-wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        mvc.perform(post("/api/v1/auth/login").contentType(JSON).content(credentials(randomUsername(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    // ------------------------------------------------------------------ protecting endpoints

    @Test @DisplayName("a protected endpoint without a token is a 401 problem document")
    void noToken() throws Exception {
        mvc.perform(get("/api/v1/teams"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test @DisplayName("a garbage token is rejected")
    void garbageToken() throws Exception {
        mvc.perform(get("/api/v1/teams").header("Authorization", bearer("not.a.jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("a valid token is accepted")
    void validToken() throws Exception {
        String token = registerAndLogin();
        mvc.perform(get("/api/v1/teams").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test @DisplayName("an expired token is rejected")
    void expiredToken() throws Exception {
        Instant past = Instant.now().minusSeconds(3600);
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(UUID.randomUUID().toString())
                .issuedAt(past.minusSeconds(60)).expiresAt(past).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/v1/teams").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("a token signed with a different key is rejected, even if its claims look right")
    void forgedToken() throws Exception {
        JwtEncoder attacker = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec("attacker-secret-attacker-secret-0123456789".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
        String forged = attacker.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/v1/teams").header("Authorization", bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("the dex and health endpoints stay public")
    void publicEndpoints() throws Exception {
        mvc.perform(get("/api/v1/species/25")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/moves/85")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
