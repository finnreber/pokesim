package com.pokesim.service;

import com.pokesim.api.dto.ActionRequest;
import com.pokesim.api.dto.ActionResponse;
import com.pokesim.api.dto.BattleSummary;
import com.pokesim.api.dto.BattleView;
import com.pokesim.api.dto.EventDto;
import com.pokesim.api.dto.LobbySummary;
import com.pokesim.api.dto.MoveView;
import com.pokesim.api.dto.PageResponse;
import com.pokesim.api.dto.PokemonView;
import com.pokesim.api.dto.SideView;
import com.pokesim.api.error.ConflictException;
import com.pokesim.api.error.NotFoundException;
import com.pokesim.engine.Action;
import com.pokesim.engine.Battle;
import com.pokesim.engine.BattleEngine;
import com.pokesim.engine.BattleEvent;
import com.pokesim.engine.BattlePokemon;
import com.pokesim.engine.BattleSide;
import com.pokesim.engine.BattleSnapshot;
import com.pokesim.engine.BattleSnapshots;
import com.pokesim.engine.BattleStatus;
import com.pokesim.engine.IllegalActionException;
import com.pokesim.engine.JavaRandomSource;
import com.pokesim.engine.Move;
import com.pokesim.engine.RandomSource;
import com.pokesim.engine.Ruleset;
import com.pokesim.engine.Side;
import com.pokesim.engine.SideSnapshot;
import com.pokesim.persistence.BattleEntity;
import com.pokesim.persistence.BattleEventEntity;
import com.pokesim.persistence.BattleEventRepository;
import com.pokesim.persistence.BattlePhase;
import com.pokesim.persistence.BattleRepository;
import com.pokesim.persistence.BattleState;
import com.pokesim.persistence.MoveEntity;
import com.pokesim.persistence.MoveRepository;
import com.pokesim.persistence.PendingAction;
import com.pokesim.persistence.TeamEntity;
import com.pokesim.persistence.TeamMemberEntity;
import com.pokesim.persistence.TeamRepository;
import com.pokesim.persistence.UserEntity;
import com.pokesim.persistence.UserRepository;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orchestrates battles: loads state, asks the pure engine to do the work, saves the result.
 * Contains no battle rules itself.
 */
@Service
public class BattleService {
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom SEEDS = new SecureRandom();

    private final BattleRepository battles;
    private final BattleEventRepository events;
    private final TeamRepository teams;
    private final MoveRepository moves;
    private final UserRepository users;
    private final Ruleset rules;
    private final TransactionTemplate tx;

    public BattleService(BattleRepository battles, BattleEventRepository events, TeamRepository teams,
                         MoveRepository moves, UserRepository users, Ruleset rules, TransactionTemplate tx) {
        this.battles = battles;
        this.events = events;
        this.teams = teams;
        this.moves = moves;
        this.users = users;
        this.rules = rules;
        this.tx = tx;
    }

    // ------------------------------------------------------------------ lobby

    @Transactional
    public BattleView create(UUID userId, UUID teamId) {
        TeamEntity team = ownedTeam(userId, teamId);
        SideSnapshot p1 = BattleSnapshots.captureSide(buildSide(Side.P1, team));
        BattleSnapshot lobby = new BattleSnapshot(p1, null, 0, BattleStatus.AWAITING_ACTIONS, List.of(), null);
        BattleEntity entity = battles.save(new BattleEntity(userId, SEEDS.nextLong(), new BattleState(lobby, null, null)));
        return view(entity, userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<LobbySummary> openLobbies(UUID userId, int page, int size) {
        Page<BattleEntity> result = battles.findByPhaseAndPlayer1IdNot(BattlePhase.WAITING_FOR_OPPONENT, userId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        Map<UUID, String> names = usernames(result.getContent());
        return PageResponse.of(result.map(b -> new LobbySummary(b.getId(), names.get(b.getPlayer1Id()), b.getCreatedAt())));
    }

    @Transactional
    public BattleView join(UUID userId, UUID battleId, UUID teamId) {
        BattleEntity b = battles.findById(battleId).orElseThrow(() -> notFound(battleId));
        if (b.getPhase() != BattlePhase.WAITING_FOR_OPPONENT) throw new ConflictException("This battle already has two players");
        if (b.getPlayer1Id().equals(userId)) throw new ConflictException("You cannot join your own battle");
        TeamEntity team = ownedTeam(userId, teamId);

        Battle battle = new Battle(BattleSnapshots.restoreSide(Side.P1, b.getState().battle().p1()), buildSide(Side.P2, team));
        b.start(userId, new BattleState(BattleSnapshots.capture(battle), null, null));
        // Flush now: if two people try to join at once, the loser fails here with an optimistic-lock conflict (409).
        battles.saveAndFlush(b);
        return view(b, userId);
    }

    @Transactional
    public void cancel(UUID userId, UUID battleId) {
        BattleEntity b = participantBattle(userId, battleId);
        if (b.getPhase() != BattlePhase.WAITING_FOR_OPPONENT) {
            throw new ConflictException("Only a battle still waiting for an opponent can be cancelled; forfeit instead");
        }
        battles.delete(b);
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public BattleView get(UUID userId, UUID battleId) {
        return view(participantBattle(userId, battleId), userId);
    }

    @Transactional(readOnly = true)
    public PageResponse<BattleSummary> mine(UUID userId, int page, int size) {
        Page<BattleEntity> result = battles.findMine(userId, PageRequest.of(page, size, Sort.by("updatedAt").descending()));
        Map<UUID, String> names = usernames(result.getContent());
        return PageResponse.of(result.map(b -> {
            Side me = sideOf(b, userId);
            UUID opponentId = me == Side.P1 ? b.getPlayer2Id() : b.getPlayer1Id();
            return new BattleSummary(b.getId(), b.getPhase().name(), opponentId == null ? null : names.get(opponentId),
                    awaitingAction(b, me), b.getUpdatedAt());
        }));
    }

    @Transactional(readOnly = true)
    public PageResponse<EventDto> events(UUID userId, UUID battleId, int page, int size) {
        participantBattle(userId, battleId);
        return PageResponse.of(events.findByBattleId(battleId, PageRequest.of(page, size, Sort.by("id"))).map(EventDto::from));
    }

    // ------------------------------------------------------------------ playing

    /**
     * Submits one player's choice. If the other player already chose, the turn resolves.
     *
     * Concurrency: both players may submit at the same instant. Each request loads the battle at some version and
     * saves with "WHERE version = ?", so only one save wins. The loser's transaction fails with an optimistic-lock
     * error; we reload the (now updated) battle and apply their action again, up to MAX_ATTEMPTS times. The
     * transaction is opened here, per attempt, so every retry starts from fresh data.
     */
    public ActionResponse submit(UUID userId, UUID battleId, ActionRequest request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> submitOnce(userId, battleId, request));
            } catch (OptimisticLockingFailureException conflict) {
                if (attempt >= MAX_ATTEMPTS) throw new ConflictException("The battle is busy right now; please retry");
            }
        }
    }

    private ActionResponse submitOnce(UUID userId, UUID battleId, ActionRequest request) {
        BattleEntity b = participantBattle(userId, battleId);
        Side side = sideOf(b, userId);
        if (b.getPhase() == BattlePhase.WAITING_FOR_OPPONENT) throw new ConflictException("Waiting for an opponent to join");
        if (b.getPhase() == BattlePhase.FINISHED) throw new ConflictException("This battle is over");

        BattleState state = b.getState();
        Battle battle = BattleSnapshots.restore(state.battle());
        Action action = Action.of(request.type(), request.index());
        BattleEngine engine = new BattleEngine(rules, rngForTurn(b.getSeed(), battle.turn() + 1));

        List<BattleEvent> produced = new ArrayList<>();
        BattleState next;
        if (battle.status() == BattleStatus.AWAITING_REPLACEMENT) {
            if (!(action instanceof Action.Switch sw)) {
                throw new IllegalActionException("A Pokemon fainted: choose one to send out with type SWITCH");
            }
            produced.addAll(engine.replaceFainted(battle, side, sw.teamIndex()));
            next = new BattleState(BattleSnapshots.capture(battle), null, null);
        } else {
            if (state.pending(side) != null) throw new ConflictException("You already chose an action this turn");
            engine.validate(battle, side, action);
            BattleState withMine = state.withPending(side, new PendingAction(request.type(), request.index()));
            PendingAction theirs = withMine.pending(side.opponent());
            if (theirs == null) {
                next = withMine;                                   // waiting for the opponent
            } else {
                produced.addAll(engine.resolveTurn(battle, withMine.pending(Side.P1).toAction(), withMine.pending(Side.P2).toAction()));
                next = new BattleState(BattleSnapshots.capture(battle), null, null);
            }
        }

        BattlePhase phase = BattlePhase.from(battle.status());
        UUID winnerId = battle.status() == BattleStatus.FINISHED && battle.winner() != null ? playerId(b, battle.winner()) : null;
        b.apply(next, phase, winnerId);
        events.saveAll(produced.stream().map(e -> new BattleEventEntity(b.getId(), battle.turn(), e)).toList());
        battles.saveAndFlush(b);   // flush inside the transaction so a version conflict is thrown here, where we retry

        BattleView view = view(b, userId);
        List<EventDto> eventDtos = produced.stream().map(e -> EventDto.of(battle.turn(), e)).toList();
        return new ActionResponse(view, eventDtos, !view.awaitingYourAction() && phase != BattlePhase.FINISHED);
    }

    @Transactional
    public BattleView forfeit(UUID userId, UUID battleId) {
        BattleEntity b = participantBattle(userId, battleId);
        if (b.getPhase() == BattlePhase.WAITING_FOR_OPPONENT) throw new ConflictException("Nobody has joined yet; cancel the battle instead");
        if (b.getPhase() == BattlePhase.FINISHED) throw new ConflictException("This battle is over");

        Side side = sideOf(b, userId);
        Battle battle = BattleSnapshots.restore(b.getState().battle());
        List<BattleEvent> produced = new BattleEngine(rules, rngForTurn(b.getSeed(), battle.turn() + 1)).forfeit(battle, side);
        b.apply(new BattleState(BattleSnapshots.capture(battle), null, null), BattlePhase.FINISHED, playerId(b, battle.winner()));
        events.saveAll(produced.stream().map(e -> new BattleEventEntity(b.getId(), battle.turn(), e)).toList());
        battles.saveAndFlush(b);
        return view(b, userId);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Each turn gets its own random stream derived from (secret seed, turn number). No hidden RNG state needs
     * storing, any turn can be replayed from the seed, and the seed itself is never sent to clients.
     */
    private static RandomSource rngForTurn(long seed, int turn) {
        return new JavaRandomSource(seed ^ (turn * 0x9E3779B97F4A7C15L));
    }

    private NotFoundException notFound(UUID battleId) {
        return new NotFoundException("No battle with id " + battleId);
    }

    private TeamEntity ownedTeam(UUID userId, UUID teamId) {
        return teams.findByIdAndOwnerId(teamId, userId).orElseThrow(() -> new NotFoundException("No team with id " + teamId));
    }

    /** Anyone who is not in the battle gets a 404, so battle ids cannot be probed. */
    private BattleEntity participantBattle(UUID userId, UUID battleId) {
        BattleEntity b = battles.findById(battleId).orElseThrow(() -> notFound(battleId));
        if (!userId.equals(b.getPlayer1Id()) && !userId.equals(b.getPlayer2Id())) throw notFound(battleId);
        return b;
    }

    private Side sideOf(BattleEntity b, UUID userId) {
        if (userId.equals(b.getPlayer1Id())) return Side.P1;
        if (userId.equals(b.getPlayer2Id())) return Side.P2;
        throw notFound(b.getId());
    }

    private UUID playerId(BattleEntity b, Side side) {
        return side == Side.P1 ? b.getPlayer1Id() : b.getPlayer2Id();
    }

    private BattleSide buildSide(Side id, TeamEntity team) {
        Set<Integer> moveIds = new HashSet<>();
        team.getMembers().forEach(m -> moveIds.addAll(m.getMoveIds()));
        Map<Integer, MoveEntity> byId = moves.findAllById(moveIds).stream().collect(Collectors.toMap(MoveEntity::getId, m -> m));
        List<BattlePokemon> party = new ArrayList<>();
        for (TeamMemberEntity member : team.getMembers()) {
            List<Move> memberMoves = member.getMoveIds().stream().map(mid -> byId.get(mid).toEngine()).toList();
            party.add(BattlePokemon.create(EngineMapper.species(member.getSpecies()), member.getLevel(), memberMoves, rules));
        }
        return new BattleSide(id, party);
    }

    private boolean awaitingAction(BattleEntity b, Side me) {
        return switch (b.getPhase()) {
            case AWAITING_ACTIONS -> b.getState().pending(me) == null;
            case AWAITING_REPLACEMENT -> b.getState().battle().pendingReplacements().contains(me);
            default -> false;
        };
    }

    private Map<UUID, String> usernames(List<BattleEntity> list) {
        Set<UUID> ids = new HashSet<>();
        for (BattleEntity b : list) {
            ids.add(b.getPlayer1Id());
            if (b.getPlayer2Id() != null) ids.add(b.getPlayer2Id());
        }
        Map<UUID, String> names = new HashMap<>();
        for (UserEntity u : users.findAllById(ids)) names.put(u.getId(), u.getUsername());
        return names;
    }

    private BattleView view(BattleEntity b, UUID viewerId) {
        Side me = sideOf(b, viewerId);
        BattleSnapshot snapshot = b.getState().battle();
        UUID opponentId = me == Side.P1 ? b.getPlayer2Id() : b.getPlayer1Id();
        SideSnapshot mine = me == Side.P1 ? snapshot.p1() : snapshot.p2();
        SideSnapshot theirs = me == Side.P1 ? snapshot.p2() : snapshot.p1();
        Map<UUID, String> names = usernames(List.of(b));

        String winner = null;
        if (b.getPhase() == BattlePhase.FINISHED) {
            winner = b.getWinnerId() == null ? "DRAW" : b.getWinnerId().equals(viewerId) ? "YOU" : "OPPONENT";
        }
        return new BattleView(b.getId(), b.getPhase().name(), snapshot.turn(), me.name(), awaitingAction(b, me), winner,
                sideView(names.get(viewerId), mine, true),
                theirs == null ? null : sideView(names.get(opponentId), theirs, false));
    }

    private SideView sideView(String username, SideSnapshot side, boolean revealMoves) {
        List<PokemonView> team = side.team().stream().map(p -> new PokemonView(p.name(),
                p.types().stream().map(Enum::name).toList(), p.level(), p.currentHp(), p.stats().hp(), p.status().name(),
                p.currentHp() <= 0,
                revealMoves ? p.moves().stream().map(m -> new MoveView(m.name(), m.type().name(), m.power(), m.pp(), m.maxPp())).toList() : null))
                .toList();
        return new SideView(username, side.activeIndex(), team);
    }
}
