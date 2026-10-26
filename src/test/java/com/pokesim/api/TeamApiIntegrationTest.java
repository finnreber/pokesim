package com.pokesim.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pokesim.persistence.MoveEntity;
import com.pokesim.persistence.MoveRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TeamApiIntegrationTest extends IntegrationTestBase {
    private static final String AUTH = "Authorization";
    private static final String VERSION_GROUP = "firered-leafgreen";

    @Autowired MoveRepository moves;

    // ------------------------------------------------------------------ helpers

    /** Real legal moves for a species, straight from the seeded learnset (species id == dex number). */
    private List<Integer> legalMoves(int dex, int count) {
        return moves.findLearnable(dex, VERSION_GROUP).stream().limit(count).map(MoveEntity::getId).toList();
    }

    private int moveId(String name) {
        return moves.findAll().stream().filter(m -> m.getName().equals(name)).findFirst().orElseThrow().getId();
    }

    private static String member(int dex, int level, List<Integer> moveIds) {
        return "{\"dexNumber\":" + dex + ",\"level\":" + level + ",\"moveIds\":["
                + moveIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + "]}";
    }

    private static String team(String name, String... members) {
        return "{\"name\":\"" + name + "\",\"members\":[" + String.join(",", members) + "]}";
    }

    private String createTeam(String token, String json) throws Exception {
        String body = mvc.perform(post("/api/v1/teams").header(AUTH, bearer(token)).contentType(JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String pikachuTeam(String name) {
        return team(name, member(25, 50, legalMoves(25, 4)));
    }

    // ------------------------------------------------------------------ creating

    @Test @DisplayName("a legal team is created and returned with species and move names")
    void createTeam() throws Exception {
        String token = registerAndLogin();
        List<Integer> ids = legalMoves(25, 4);
        mvc.perform(post("/api/v1/teams").header(AUTH, bearer(token)).contentType(JSON)
                        .content(team("Kanto Squad", member(25, 50, ids))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/teams/")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Kanto Squad"))
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].species").value("pikachu"))
                .andExpect(jsonPath("$.members[0].level").value(50))
                .andExpect(jsonPath("$.members[0].moves.length()").value(4))
                .andExpect(jsonPath("$.members[0].moves[0].id").value(ids.get(0)))
                .andExpect(jsonPath("$.members[0].moves[0].name").isNotEmpty());
    }

    @Test @DisplayName("a full party of six keeps its order")
    void fullParty() throws Exception {
        String token = registerAndLogin();
        int[] dex = {1, 4, 7, 25, 39, 52};   // bulbasaur, charmander, squirtle, pikachu, jigglypuff, meowth
        String[] members = new String[6];
        for (int i = 0; i < 6; i++) members[i] = member(dex[i], 50, legalMoves(dex[i], 2));
        mvc.perform(post("/api/v1/teams").header(AUTH, bearer(token)).contentType(JSON).content(team("Party", members)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.members.length()").value(6))
                .andExpect(jsonPath("$.members[0].species").value("bulbasaur"))
                .andExpect(jsonPath("$.members[5].species").value("meowth"));
    }

    @Test @DisplayName("a team needs a login")
    void createRequiresAuth() throws Exception {
        mvc.perform(post("/api/v1/teams").contentType(JSON).content(pikachuTeam("Nope")))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ legality (422)

    @Test @DisplayName("a move the species cannot learn in this game is a 422 that says which one")
    void illegalMove() throws Exception {
        String token = registerAndLogin();
        mvc.perform(post("/api/v1/teams").header(AUTH, bearer(token)).contentType(JSON)
                        .content(team("Bad", member(25, 50, List.of(moveId("surf"))))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errors[0]").value(containsString("pikachu cannot learn")));
    }

    @Test @DisplayName("an unknown species and a repeated move are both reported in one response")
    void allProblemsAtOnce() throws Exception {
        String token = registerAndLogin();
        int tackleLike = legalMoves(25, 1).get(0);
        mvc.perform(post("/api/v1/teams").header(AUTH, bearer(token)).contentType(JSON)
                        .content(team("Bad", member(999, 50, List.of(1)), member(25, 50, List.of(tackleLike, tackleLike)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0]").value(containsString("members[0]: species 999")))
                .andExpect(jsonPath("$.errors[1]").value(containsString("members[1]: move")));
    }

    // ------------------------------------------------------------------ shape validation (400)

    @Test @DisplayName("structurally invalid teams are 400s")
    void structuralValidation() throws Exception {
        String token = registerAndLogin();
        List<Integer> four = legalMoves(25, 4);
        String ok = member(25, 50, four);
        String[] bad = {
                team("No members"),                                                             // 0 members
                team("Seven", ok, ok, ok, ok, ok, ok, ok),                                       // 7 members
                team("Level", member(25, 101, four)),                                            // level too high
                team("Level zero", member(25, 0, four)),
                team("", ok),                                                                    // blank name
                team("Five moves", member(25, 50, List.of(1, 2, 3, 4, 5))),                     // > 4 moves
                team("No moves", member(25, 50, List.of())),
        };
        for (String json : bad) {
            mvc.perform(post("/api/v1/teams").header(AUTH, bearer(token)).contentType(JSON).content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    // ------------------------------------------------------------------ ownership

    @Test @DisplayName("another player's team looks like it does not exist: read, update and delete are all 404")
    void ownershipIsEnforced() throws Exception {
        String alice = registerAndLogin();
        String bob = registerAndLogin();
        String teamId = createTeam(alice, pikachuTeam("Alice's team"));

        mvc.perform(get("/api/v1/teams/" + teamId).header(AUTH, bearer(bob))).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/teams/" + teamId).header(AUTH, bearer(bob)).contentType(JSON).content(pikachuTeam("Stolen")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/teams/" + teamId).header(AUTH, bearer(bob))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/teams").header(AUTH, bearer(bob))).andExpect(jsonPath("$.items.length()").value(0));

        // and Alice's team is untouched
        mvc.perform(get("/api/v1/teams/" + teamId).header(AUTH, bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice's team"));
    }

    // ------------------------------------------------------------------ update and delete

    @Test @DisplayName("PUT replaces the whole party (old rows deleted before new ones inserted)")
    void replaceMembers() throws Exception {
        String token = registerAndLogin();
        String teamId = createTeam(token, pikachuTeam("Evolving"));

        String update = team("Evolved", member(6, 60, legalMoves(6, 3)), member(25, 55, legalMoves(25, 2)));
        mvc.perform(put("/api/v1/teams/" + teamId).header(AUTH, bearer(token)).contentType(JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Evolved"))
                .andExpect(jsonPath("$.members.length()").value(2));

        mvc.perform(get("/api/v1/teams/" + teamId).header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].species").value("charizard"))
                .andExpect(jsonPath("$.members[0].level").value(60))
                .andExpect(jsonPath("$.members[1].species").value("pikachu"))
                .andExpect(jsonPath("$.members[1].moves.length()").value(2));
    }

    @Test @DisplayName("an illegal update is rejected and leaves the team unchanged")
    void failedUpdateChangesNothing() throws Exception {
        String token = registerAndLogin();
        String teamId = createTeam(token, pikachuTeam("Stable"));
        mvc.perform(put("/api/v1/teams/" + teamId).header(AUTH, bearer(token)).contentType(JSON)
                        .content(team("Broken", member(25, 50, List.of(moveId("surf"))))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/teams/" + teamId).header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.name").value("Stable"))
                .andExpect(jsonPath("$.members[0].moves.length()").value(4));
    }

    @Test @DisplayName("delete removes the team: 204, then 404")
    void deleteTeam() throws Exception {
        String token = registerAndLogin();
        String teamId = createTeam(token, pikachuTeam("Temporary"));
        mvc.perform(delete("/api/v1/teams/" + teamId).header(AUTH, bearer(token))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/teams/" + teamId).header(AUTH, bearer(token))).andExpect(status().isNotFound());
    }

    @Test @DisplayName("unknown and malformed team ids")
    void badIds() throws Exception {
        String token = registerAndLogin();
        mvc.perform(get("/api/v1/teams/" + UUID.randomUUID()).header(AUTH, bearer(token))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/teams/not-a-uuid").header(AUTH, bearer(token))).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ names and paging

    @Test @DisplayName("team names are unique per player, but two players may reuse a name")
    void duplicateNames() throws Exception {
        String alice = registerAndLogin();
        String bob = registerAndLogin();
        createTeam(alice, pikachuTeam("Favourites"));
        mvc.perform(post("/api/v1/teams").header(AUTH, bearer(alice)).contentType(JSON).content(pikachuTeam("Favourites")))
                .andExpect(status().isConflict());
        createTeam(bob, pikachuTeam("Favourites"));
    }

    @Test @DisplayName("renaming a team to another of your team names is a conflict, keeping its own name is fine")
    void renameConflict() throws Exception {
        String token = registerAndLogin();
        createTeam(token, pikachuTeam("First"));
        String secondId = createTeam(token, pikachuTeam("Second"));
        mvc.perform(put("/api/v1/teams/" + secondId).header(AUTH, bearer(token)).contentType(JSON).content(pikachuTeam("First")))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/teams/" + secondId).header(AUTH, bearer(token)).contentType(JSON).content(pikachuTeam("Second")))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("listing is paginated and only includes your own teams")
    void pagination() throws Exception {
        String token = registerAndLogin();
        for (String name : List.of("One", "Two", "Three")) createTeam(token, pikachuTeam(name));
        createTeam(registerAndLogin(), pikachuTeam("Someone else's"));

        mvc.perform(get("/api/v1/teams?size=2").header(AUTH, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/teams?size=2&page=1").header(AUTH, bearer(token)))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/teams?size=0").header(AUTH, bearer(token))).andExpect(status().isBadRequest());
    }
}
