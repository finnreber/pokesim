package com.pokesim.service;

import com.pokesim.api.dto.EventDto;
import com.pokesim.api.dto.SimulationResult;
import com.pokesim.engine.Action;
import com.pokesim.engine.Battle;
import com.pokesim.engine.BattleEngine;
import com.pokesim.engine.BattleEvent;
import com.pokesim.engine.BattlePokemon;
import com.pokesim.engine.BattleSide;
import com.pokesim.engine.BattleStatus;
import com.pokesim.engine.JavaRandomSource;
import com.pokesim.engine.Move;
import com.pokesim.engine.Ruleset;
import com.pokesim.engine.Side;
import com.pokesim.persistence.MoveEntity;
import com.pokesim.persistence.MoveRepository;
import com.pokesim.persistence.SpeciesEntity;
import com.pokesim.persistence.SpeciesRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public demo: two random legal teams fight to the end with random choices. Needs no login and stores nothing.
 * Fully deterministic: the same seed and team size always produce the identical battle.
 */
@Service
@Transactional(readOnly = true)
public class SimulationService {
    private static final int LEVEL = 50;
    private static final int MAX_TURNS = 300;   // hard stop: a public endpoint must never loop forever

    private final SpeciesRepository species;
    private final MoveRepository moves;
    private final Ruleset rules;

    public SimulationService(SpeciesRepository species, MoveRepository moves, Ruleset rules) {
        this.species = species;
        this.moves = moves;
        this.rules = rules;
    }

    public SimulationResult simulate(Long requestedSeed, Integer requestedSize) {
        long seed = requestedSeed != null ? requestedSeed : ThreadLocalRandom.current().nextLong();
        int size = requestedSize != null ? requestedSize : 6;
        Random choices = new Random(seed);

        List<SpeciesEntity> all = species.findAll(Sort.by("dexNumber"));   // fixed order => same seed, same teams
        BattleSide p1 = randomSide(Side.P1, all, size, choices);
        BattleSide p2 = randomSide(Side.P2, all, size, choices);
        Battle battle = new Battle(p1, p2);
        BattleEngine engine = new BattleEngine(rules, new JavaRandomSource(seed));

        List<EventDto> log = new ArrayList<>();
        while (battle.status() != BattleStatus.FINISHED && battle.turn() < MAX_TURNS) {
            List<BattleEvent> produced = new ArrayList<>();
            if (battle.status() == BattleStatus.AWAITING_REPLACEMENT) {
                for (Side side : List.copyOf(battle.pendingReplacements())) {
                    produced.addAll(engine.replaceFainted(battle, side, randomAlive(battle.side(side), choices)));
                }
            } else {
                produced.addAll(engine.resolveTurn(battle, choose(battle, Side.P1, choices), choose(battle, Side.P2, choices)));
            }
            int turn = battle.turn();
            produced.forEach(e -> log.add(EventDto.of(turn, e)));
        }

        String winner = battle.status() != BattleStatus.FINISHED ? "UNDECIDED"
                : battle.winner() == null ? "DRAW" : battle.winner().name();
        return new SimulationResult(seed, battle.turn(), winner, names(p1), names(p2), log);
    }

    private BattleSide randomSide(Side id, List<SpeciesEntity> all, int size, Random random) {
        List<SpeciesEntity> candidates = new ArrayList<>(all);
        Collections.shuffle(candidates, random);
        List<BattlePokemon> party = new ArrayList<>();
        for (SpeciesEntity s : candidates) {
            if (party.size() == size) break;
            List<MoveEntity> learnable = new ArrayList<>(moves.findLearnable(s.getId(), DexService.VERSION_GROUP));
            List<MoveEntity> damaging = learnable.stream().filter(m -> m.getPower() > 0).toList();
            if (damaging.isEmpty()) continue;   // a Pokemon with no attack could stall the demo forever
            MoveEntity firstAttack = damaging.get(random.nextInt(damaging.size()));
            learnable.remove(firstAttack);
            Collections.shuffle(learnable, random);
            List<Move> chosen = new ArrayList<>(List.of(firstAttack.toEngine()));
            learnable.stream().limit(3).forEach(m -> chosen.add(m.toEngine()));
            party.add(BattlePokemon.create(EngineMapper.species(s), LEVEL, chosen, rules));
        }
        return new BattleSide(id, party);
    }

    /** Mostly picks a move that still has PP; occasionally switches to a healthy teammate. */
    private Action choose(Battle battle, Side side, Random random) {
        BattleSide bs = battle.side(side);
        List<Integer> bench = new ArrayList<>();
        for (int i = 0; i < bs.team().size(); i++) {
            if (i != bs.activeIndex() && !bs.team().get(i).isFainted()) bench.add(i);
        }
        if (!bench.isEmpty() && random.nextInt(10) == 0) return new Action.Switch(bench.get(random.nextInt(bench.size())));

        BattlePokemon active = bs.active();
        List<Integer> usable = new ArrayList<>();
        for (int i = 0; i < active.moves().size(); i++) if (active.moves().get(i).pp() > 0) usable.add(i);
        return new Action.UseMove(usable.isEmpty() ? 0 : usable.get(random.nextInt(usable.size())));   // 0 = Struggle
    }

    private int randomAlive(BattleSide side, Random random) {
        List<Integer> alive = new ArrayList<>();
        for (int i = 0; i < side.team().size(); i++) if (!side.team().get(i).isFainted()) alive.add(i);
        return alive.get(random.nextInt(alive.size()));
    }

    private List<String> names(BattleSide side) {
        return side.team().stream().map(BattlePokemon::name).toList();
    }
}
