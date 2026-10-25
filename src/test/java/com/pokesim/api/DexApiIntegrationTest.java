package com.pokesim.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-stack test: real PostgreSQL in Docker, real Flyway migrations (including the generated seed data),
 * real Hibernate schema validation, real HTTP layer. If a migration, entity mapping or endpoint is wrong,
 * this is where it fails, not in production.
 *
 * Requires Docker to be running (locally and in GitHub Actions).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DexApiIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;

    // ------------------------------------------------------------------ species

    @Test @DisplayName("Pikachu's detail comes from the seeded data")
    void speciesDetail() throws Exception {
        mvc.perform(get("/api/v1/species/25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("pikachu"))
                .andExpect(jsonPath("$.types[0]").value("ELECTRIC"))
                .andExpect(jsonPath("$.baseStats.speed").value(90));
    }

    @Test @DisplayName("generation-correct data survives end to end: Clefairy is Normal in FireRed")
    void clefairyIsNormalInGen3() throws Exception {
        mvc.perform(get("/api/v1/species/35"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.types.length()").value(1))
                .andExpect(jsonPath("$.types[0]").value("NORMAL"));
    }

    @Test @DisplayName("a dex number that is not in the game returns a 404 problem document")
    void unknownSpecies() throws Exception {
        mvc.perform(get("/api/v1/species/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No species with dex number 999 in this game"));
    }

    @Test @DisplayName("listing is paginated and ordered by dex number")
    void speciesPagination() throws Exception {
        mvc.perform(get("/api/v1/species"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[0].dexNumber").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mvc.perform(get("/api/v1/species?page=1&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test @DisplayName("a page past the end is empty, not an error")
    void pageBeyondTheEnd() throws Exception {
        mvc.perform(get("/api/v1/species?page=500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test @DisplayName("type filter is case-insensitive and matches primary or secondary type")
    void filterByType() throws Exception {
        mvc.perform(get("/api/v1/species?type=electric&size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.name == 'pikachu')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.name == 'bulbasaur')]").isEmpty());

        mvc.perform(get("/api/v1/species?type=flying&size=100"))   // Charizard is Fire/Flying
                .andExpect(jsonPath("$.items[?(@.name == 'charizard')]").isNotEmpty());
    }

    @Test @DisplayName("name search is a case-insensitive contains match")
    void searchByName() throws Exception {
        mvc.perform(get("/api/v1/species?name=CHAR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.name == 'charmander')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.name == 'charizard')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.name == 'pikachu')]").isEmpty());
    }

    @Test @DisplayName("LIKE wildcards in the search are matched literally")
    void wildcardsAreEscaped() throws Exception {
        mvc.perform(get("/api/v1/species?name=%25"))   // a literal %
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test @DisplayName("invalid paging and filter values are 400s with a problem document")
    void badInput() throws Exception {
        mvc.perform(get("/api/v1/species?size=1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/species?size=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/species?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/species?type=banana"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Unknown type 'banana'")));
    }

    // ------------------------------------------------------------------ moves

    @Test @DisplayName("Thunderbolt has its Gen 3 stats, and category comes from the ruleset")
    void thunderbolt() throws Exception {
        mvc.perform(get("/api/v1/moves/85"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("thunderbolt"))
                .andExpect(jsonPath("$.type").value("ELECTRIC"))
                .andExpect(jsonPath("$.power").value(95))
                .andExpect(jsonPath("$.category").value("SPECIAL"))
                .andExpect(jsonPath("$.ailment").value("PARALYSIS"))
                .andExpect(jsonPath("$.ailmentChance").value(10));
    }

    @Test @DisplayName("Toxic is a status move that badly poisons")
    void toxic() throws Exception {
        mvc.perform(get("/api/v1/moves?name=toxic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.name == 'toxic')].ailment").value("BADLY_POISONED"))
                .andExpect(jsonPath("$.items[?(@.name == 'toxic')].category").value("STATUS"));
    }

    @Test @DisplayName("move list filters by type")
    void movesByType() throws Exception {
        mvc.perform(get("/api/v1/moves?type=fire&size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.items[?(@.type != 'FIRE')]").isEmpty());
    }

    @Test @DisplayName("unknown move id is a 404")
    void unknownMove() throws Exception {
        mvc.perform(get("/api/v1/moves/99999")).andExpect(status().isNotFound());
    }

    @Test @DisplayName("a species' learnset comes from the join table for this game")
    void learnset() throws Exception {
        mvc.perform(get("/api/v1/species/25/moves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'thunderbolt')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.name == 'surf')]").isEmpty());   // Pikachu cannot learn Surf in FireRed
    }

    @Test @DisplayName("health endpoint is up")
    void health() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
