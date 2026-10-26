package com.pokesim.api;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SimulationApiIntegrationTest extends IntegrationTestBase {

    private String simulate(String body) throws Exception {
        return mvc.perform(post("/api/v1/simulate").contentType(JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test @DisplayName("anyone can run a simulation without logging in, and it plays a complete battle")
    void publicSimulation() throws Exception {
        mvc.perform(post("/api/v1/simulate").contentType(JSON).content("{\"seed\":42,\"teamSize\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seed").value(42))
                .andExpect(jsonPath("$.p1Team.length()").value(3))
                .andExpect(jsonPath("$.p2Team.length()").value(3))
                .andExpect(jsonPath("$.turns").value(greaterThan(0)))
                .andExpect(jsonPath("$.winner").value(matchesPattern("P1|P2|DRAW")))
                .andExpect(jsonPath("$.winner").value(not("UNDECIDED")))
                .andExpect(jsonPath("$.events[?(@.type == 'MOVE_USED')]").isNotEmpty())
                .andExpect(jsonPath("$.events[?(@.type == 'BATTLE_END')]").isNotEmpty());
    }

    @Test @DisplayName("the same seed gives exactly the same battle; a different seed gives a different one")
    void deterministic() throws Exception {
        String first = simulate("{\"seed\":7,\"teamSize\":2}");
        String second = simulate("{\"seed\":7,\"teamSize\":2}");
        assertEquals(first, second);
        assertNotEquals(first, simulate("{\"seed\":8,\"teamSize\":2}"));
    }

    @Test @DisplayName("with no body it picks a random seed and a full team of six")
    void defaults() throws Exception {
        mvc.perform(post("/api/v1/simulate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.p1Team.length()").value(6))
                .andExpect(jsonPath("$.p2Team.length()").value(6));
        mvc.perform(post("/api/v1/simulate").contentType(JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("team size outside 1-6 is rejected")
    void validation() throws Exception {
        mvc.perform(post("/api/v1/simulate").contentType(JSON).content("{\"teamSize\":7}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/simulate").contentType(JSON).content("{\"teamSize\":0}")).andExpect(status().isBadRequest());
    }
}
