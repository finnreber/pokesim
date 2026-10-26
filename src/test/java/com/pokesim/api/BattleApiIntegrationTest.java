package com.pokesim.api;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.pokesim.persistence.MoveEntity;
import com.pokesim.persistence.MoveRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BattleApiIntegrationTest extends IntegrationTestBase {
    private static final String AUTH = "Authorization";
    private static final String VERSION_GROUP = "firered-leafgreen";

    @Autowired MoveRepository moves;

    /** Two players in a started battle. Player A hosted, player B joined. */
    private record Match(String tokenA, String tokenB, String battleId) { }

    // ------------------------------------------------------------------ helpers

    private List<Integer> damagingMoves(int dex, int count) {
        return moves.findLearnable(dex, VERSION_GROUP).stream().filter(m -> m.getPower() > 0)
                .limit(count).map(MoveEntity::getId).toList();
    }

    private String teamJson(String name, int... dexNumbers) {
        String members = java.util.Arrays.stream(dexNumbers).mapToObj(dex ->
                "{\"dexNumber\":" + dex + ",\"level\":50,\"moveIds\":["
                        + damagingMoves(dex, 2).stream().map(String::valueOf).collect(Collectors.joining(",")) + "]}")
                .collect(Collectors.joining(","));
        return "{\"name\":\"" + name + "\",\"members\":[" + members + "]}";
    }

    private String createTeam(String token, int... dexNumbers) throws Exception {
        String body = mvc.perform(post("/api/v1/teams").header(AUTH, bearer(token)).contentType(JSON)
                        .content(teamJson("Team-" + UUID.randomUUID().toString().substring(0, 8), dexNumbers)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String teamRef(String teamId) {
        return "{\"teamId\":\"" + teamId + "\"}";
    }

    private String createLobby(String token, int... dexNumbers) throws Exception {
        String body = mvc.perform(post("/api/v1/battles").header(AUTH, bearer(token)).contentType(JSON)
                        .content(teamRef(createTeam(token, dexNumbers))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void join(String token, String battleId, int... dexNumbers) throws Exception {
        mvc.perform(post("/api/v1/battles/" + battleId + "/join").header(AUTH, bearer(token)).contentType(JSON)
                        .content(teamRef(createTeam(token, dexNumbers))))
                .andExpect(status().isOk());
    }

    private Match startMatch(int[] teamA, int[] teamB) throws Exception {
        String a = registerAndLogin();
        String b = registerAndLogin();
        String id = createLobby(a, teamA);
        join(b, id, teamB);
        return new Match(a, b, id);
    }

    private Match startMatch() throws Exception {
        return startMatch(new int[]{25}, new int[]{7});   // Pikachu vs Squirtle
    }

    private int act(String token, String battleId, String type, int index) throws Exception {
        return mvc.perform(post("/api/v1/battles/" + battleId + "/actions").header(AUTH, bearer(token)).contentType(JSON)
                        .content("{\"type\":\"" + type + "\",\"index\":" + index + "}"))
                .andReturn().getResponse().getStatus();
    }

    private String view(String token, String battleId) throws Exception {
        return mvc.perform(get("/api/v1/battles/" + battleId).header(AUTH, bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------------ lobby

    @Test @DisplayName("hosting a battle creates a lobby that shows your team and no opponent")
    void createLobby() throws Exception {
        String token = registerAndLogin();
        String teamId = createTeam(token, 25);
        mvc.perform(post("/api/v1/battles").header(AUTH, bearer(token)).contentType(JSON).content(teamRef(teamId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/battles/")))
                .andExpect(jsonPath("$.phase").value("WAITING_FOR_OPPONENT"))
                .andExpect(jsonPath("$.youAre").value("P1"))
                .andExpect(jsonPath("$.opponent").doesNotExist())
                .andExpect(jsonPath("$.you.team[0].species").value("pikachu"))
                .andExpect(jsonPath("$.you.team[0].moves.length()").value(2));
    }

    @Test @DisplayName("you cannot battle with someone else's team")
    void foreignTeam() throws Exception {
        String alice = registerAndLogin();
        String bob = registerAndLogin();
        String alicesTeam = createTeam(alice, 25);
        mvc.perform(post("/api/v1/battles").header(AUTH, bearer(bob)).contentType(JSON).content(teamRef(alicesTeam)))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("open lobbies list other players' lobbies, not your own")
    void openLobbies() throws Exception {
        String host = registerAndLogin();
        String other = registerAndLogin();
        String id = createLobby(host, 25);
        String filter = "$.items[?(@.id == '" + id + "')]";
        mvc.perform(get("/api/v1/battles/open?size=100").header(AUTH, bearer(other)))
                .andExpect(status().isOk()).andExpect(jsonPath(filter).isNotEmpty());
        mvc.perform(get("/api/v1/battles/open?size=100").header(AUTH, bearer(host)))
                .andExpect(jsonPath(filter).isEmpty());
    }

    @Test @DisplayName("joining starts the battle; the opponent's moves stay hidden but yours are shown")
    void joinStartsBattle() throws Exception {
        String host = registerAndLogin();
        String guest = registerAndLogin();
        String id = createLobby(host, 25);
        String guestTeam = createTeam(guest, 7);
        mvc.perform(post("/api/v1/battles/" + id + "/join").header(AUTH, bearer(guest)).contentType(JSON).content(teamRef(guestTeam)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("AWAITING_ACTIONS"))
                .andExpect(jsonPath("$.youAre").value("P2"))
                .andExpect(jsonPath("$.awaitingYourAction").value(true))
                .andExpect(jsonPath("$.you.team[0].species").value("squirtle"))
                .andExpect(jsonPath("$.you.team[0].moves.length()").value(2))
                .andExpect(jsonPath("$.opponent.team[0].species").value("pikachu"))
                .andExpect(jsonPath("$.opponent.team[0].moves").doesNotExist());
    }

    @Test @DisplayName("joining rules: not your own lobby, not a full battle, not with someone else's team")
    void joinRules() throws Exception {
        Match m = startMatch();
        String third = registerAndLogin();
        String thirdTeam = createTeam(third, 4);
        mvc.perform(post("/api/v1/battles/" + m.battleId() + "/join").header(AUTH, bearer(third)).contentType(JSON).content(teamRef(thirdTeam)))
                .andExpect(status().isConflict());

        String host = registerAndLogin();
        String lobby = createLobby(host, 25);
        mvc.perform(post("/api/v1/battles/" + lobby + "/join").header(AUTH, bearer(host)).contentType(JSON)
                        .content(teamRef(createTeam(host, 4))))
                .andExpect(status().isConflict());                                     // own lobby
        mvc.perform(post("/api/v1/battles/" + lobby + "/join").header(AUTH, bearer(third)).contentType(JSON)
                        .content(teamRef(createTeam(host, 4))))
                .andExpect(status().isNotFound());                                     // host's team, not third's
        mvc.perform(post("/api/v1/battles/" + UUID.randomUUID() + "/join").header(AUTH, bearer(third)).contentType(JSON).content(teamRef(thirdTeam)))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("a lobby can be cancelled by its host only, and only before someone joins")
    void cancelLobby() throws Exception {
        String host = registerAndLogin();
        String other = registerAndLogin();
        String id = createLobby(host, 25);
        mvc.perform(delete("/api/v1/battles/" + id).header(AUTH, bearer(other))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/battles/" + id).header(AUTH, bearer(host))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/battles/" + id).header(AUTH, bearer(host))).andExpect(status().isNotFound());

        Match m = startMatch();
        mvc.perform(delete("/api/v1/battles/" + m.battleId()).header(AUTH, bearer(m.tokenA()))).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ privacy

    @Test @DisplayName("outsiders and anonymous users cannot see a battle")
    void privacy() throws Exception {
        Match m = startMatch();
        String outsider = registerAndLogin();
        mvc.perform(get("/api/v1/battles/" + m.battleId()).header(AUTH, bearer(outsider))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/battles/" + m.battleId() + "/events").header(AUTH, bearer(outsider))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/battles/" + m.battleId())).andExpect(status().isUnauthorized());
        assertEquals(404, act(outsider, m.battleId(), "MOVE", 0));
    }

    // ------------------------------------------------------------------ turns

    @Test @DisplayName("the first submission waits; the second resolves the turn and logs events")
    void turnResolvesWhenBothSubmit() throws Exception {
        Match m = startMatch();

        mvc.perform(post("/api/v1/battles/" + m.battleId() + "/actions").header(AUTH, bearer(m.tokenA())).contentType(JSON)
                        .content("{\"type\":\"MOVE\",\"index\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingForOpponent").value(true))
                .andExpect(jsonPath("$.events.length()").value(0))
                .andExpect(jsonPath("$.battle.turn").value(0))
                .andExpect(jsonPath("$.battle.awaitingYourAction").value(false));

        // the opponent is still being asked, and nothing about A's choice leaks into B's view
        String bView = view(m.tokenB(), m.battleId());
        assertEquals(true, JsonPath.read(bView, "$.awaitingYourAction"));
        assertTrue(!bView.contains("pending"), "B's view must not mention the pending action");

        mvc.perform(post("/api/v1/battles/" + m.battleId() + "/actions").header(AUTH, bearer(m.tokenB())).contentType(JSON)
                        .content("{\"type\":\"MOVE\",\"index\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.battle.turn").value(1))
                .andExpect(jsonPath("$.events.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.events[?(@.type == 'MOVE_USED')]").isNotEmpty());

        mvc.perform(get("/api/v1/battles/" + m.battleId() + "/events?size=2").header(AUTH, bearer(m.tokenA())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThan(2)))
                .andExpect(jsonPath("$.items[0].turn").value(1));
    }

    @Test @DisplayName("you cannot choose twice in the same turn")
    void duplicateAction() throws Exception {
        Match m = startMatch();
        assertEquals(200, act(m.tokenA(), m.battleId(), "MOVE", 0));
        assertEquals(409, act(m.tokenA(), m.battleId(), "MOVE", 1));
    }

    @Test @DisplayName("illegal or malformed actions are rejected before anything changes")
    void illegalActions() throws Exception {
        Match m = startMatch();
        assertEquals(422, act(m.tokenA(), m.battleId(), "MOVE", 9));        // no such move slot
        assertEquals(422, act(m.tokenA(), m.battleId(), "SWITCH", 0));      // already in battle (and no bench)
        assertEquals(400, act(m.tokenA(), m.battleId(), "MOVE", -1));       // negative index
        mvc.perform(post("/api/v1/battles/" + m.battleId() + "/actions").header(AUTH, bearer(m.tokenA())).contentType(JSON)
                        .content("{\"type\":\"DANCE\",\"index\":0}"))
                .andExpect(status().isBadRequest());
        // and A can still make a legal choice afterwards
        assertEquals(200, act(m.tokenA(), m.battleId(), "MOVE", 0));
    }

    @Test @DisplayName("acting in a lobby that nobody joined is a conflict")
    void actingInLobby() throws Exception {
        String host = registerAndLogin();
        String id = createLobby(host, 25);
        assertEquals(409, act(host, id, "MOVE", 0));
    }

    // ------------------------------------------------------------------ finishing

    @Test @DisplayName("forfeiting ends the battle: the opponent wins, and nothing more can be done")
    void forfeit() throws Exception {
        Match m = startMatch();
        mvc.perform(post("/api/v1/battles/" + m.battleId() + "/forfeit").header(AUTH, bearer(m.tokenA())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("FINISHED"))
                .andExpect(jsonPath("$.winner").value("OPPONENT"));
        mvc.perform(get("/api/v1/battles/" + m.battleId()).header(AUTH, bearer(m.tokenB())))
                .andExpect(jsonPath("$.winner").value("YOU"));
        assertEquals(409, act(m.tokenB(), m.battleId(), "MOVE", 0));
        mvc.perform(post("/api/v1/battles/" + m.battleId() + "/forfeit").header(AUTH, bearer(m.tokenB())))
                .andExpect(status().isConflict());
    }

    @Test @DisplayName("a whole battle, with fainting and replacements, can be played to the end over HTTP")
    void playToTheEnd() throws Exception {
        Match m = startMatch(new int[]{25, 4}, new int[]{7, 1});   // Pikachu+Charmander vs Squirtle+Bulbasaur

        for (int i = 0; i < 800; i++) {
            if ("FINISHED".equals(JsonPath.read(view(m.tokenA(), m.battleId()), "$.phase"))) break;
            for (String token : List.of(m.tokenA(), m.tokenB())) {
                String mine = view(token, m.battleId());
                boolean awaiting = JsonPath.read(mine, "$.awaitingYourAction");
                if (!awaiting) continue;
                if ("AWAITING_REPLACEMENT".equals(JsonPath.read(mine, "$.phase"))) {
                    List<Boolean> fainted = JsonPath.read(mine, "$.you.team[*].fainted");
                    assertEquals(200, act(token, m.battleId(), "SWITCH", fainted.indexOf(false)));
                } else {
                    assertEquals(200, act(token, m.battleId(), "MOVE", 0));
                }
            }
        }

        String a = view(m.tokenA(), m.battleId());
        String b = view(m.tokenB(), m.battleId());
        assertEquals("FINISHED", JsonPath.read(a, "$.phase"));
        String winnerA = JsonPath.read(a, "$.winner");
        String winnerB = JsonPath.read(b, "$.winner");
        switch (winnerA) {
            case "YOU" -> assertEquals("OPPONENT", winnerB);
            case "OPPONENT" -> assertEquals("YOU", winnerB);
            default -> assertEquals("DRAW", winnerB);
        }
        mvc.perform(get("/api/v1/battles/" + m.battleId() + "/events?size=200").header(AUTH, bearer(m.tokenA())))
                .andExpect(jsonPath("$.items[?(@.type == 'FAINTED')]").isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.type == 'BATTLE_END')]").isNotEmpty());
        assertEquals(409, act(m.tokenA(), m.battleId(), "MOVE", 0));
    }

    @Test @DisplayName("my battles lists the battles I am in, with my opponent's name")
    void myBattles() throws Exception {
        Match m = startMatch();
        mvc.perform(get("/api/v1/battles").header(AUTH, bearer(m.tokenA())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == '" + m.battleId() + "')]").isNotEmpty())
                .andExpect(jsonPath("$.items[0].opponent").isNotEmpty());
        mvc.perform(get("/api/v1/battles").header(AUTH, bearer(registerAndLogin())))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // ------------------------------------------------------------------ concurrency

    @Test @DisplayName("both players submitting at the same instant both succeed (version conflicts are retried)")
    void simultaneousSubmissions() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 12; round++) {
                Match m = startMatch();
                CountDownLatch go = new CountDownLatch(1);
                Callable<Integer> playerA = () -> { go.await(); return act(m.tokenA(), m.battleId(), "MOVE", 0); };
                Callable<Integer> playerB = () -> { go.await(); return act(m.tokenB(), m.battleId(), "MOVE", 0); };
                Future<Integer> a = pool.submit(playerA);
                Future<Integer> b = pool.submit(playerB);
                go.countDown();

                assertEquals(200, a.get(60, TimeUnit.SECONDS), "player A, round " + round);
                assertEquals(200, b.get(60, TimeUnit.SECONDS), "player B, round " + round);
                // exactly one turn was resolved: neither action was lost or applied twice
                assertEquals(1, (int) JsonPath.read(view(m.tokenA(), m.battleId()), "$.turn"), "turn count, round " + round);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
