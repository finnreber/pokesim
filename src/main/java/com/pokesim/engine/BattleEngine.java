package com.pokesim.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves one turn of a FireRed-style singles battle. No Spring, no database, no clock:
 * (battle state + two actions + a RandomSource) in, (mutated state + event log) out.
 *
 * Turn order: switches first, then moves by priority bracket, then speed (ties broken randomly).
 * After all actions: burn/poison damage. Then faint handling / win check.
 */
public final class BattleEngine {
    private static final int SWITCH_PRIORITY = 6;

    private final Ruleset rules;
    private final DamageCalculator calculator;
    private final RandomSource rng;

    public BattleEngine(Ruleset rules, RandomSource rng) {
        this.rules = rules;
        this.calculator = new DamageCalculator(rules);
        this.rng = rng;
    }

    private record Queued(Side side, Action action, int priority, int speed) {}

    // ------------------------------------------------------------------ public API

    public List<BattleEvent> resolveTurn(Battle battle, Action p1Action, Action p2Action) {
        requireStatus(battle, BattleStatus.AWAITING_ACTIONS);
        validate(battle, Side.P1, p1Action);
        validate(battle, Side.P2, p2Action);

        List<BattleEvent> events = new ArrayList<>();
        battle.advanceTurn();
        events.add(new BattleEvent(EventType.TURN_START, null, "Turn " + battle.turn() + " begins.", battle.turn()));

        Queued q1 = enqueue(battle, Side.P1, p1Action);
        Queued q2 = enqueue(battle, Side.P2, p2Action);
        for (Queued q : executionOrder(q1, q2)) {
            if (battle.side(q.side()).active().isFainted()) continue; // KO'd earlier this turn
            switch (q.action()) {
                case Action.Switch s -> switchTo(battle, q.side(), s.teamIndex(), events);
                case Action.UseMove m -> useMove(battle, q.side(), m.moveIndex(), events);
            }
        }
        applyResidualDamage(battle, events);
        settle(battle, events);
        return events;
    }

    /** After a faint, the owner picks the next Pokemon. Both players may do this in either order. */
    public List<BattleEvent> replaceFainted(Battle battle, Side side, int teamIndex) {
        requireStatus(battle, BattleStatus.AWAITING_REPLACEMENT);
        if (!battle.pendingReplacements().contains(side)) {
            throw new IllegalActionException(side + " does not need to replace a Pokemon");
        }
        validateSwitchTarget(battle.side(side), teamIndex);

        List<BattleEvent> events = new ArrayList<>();
        battle.side(side).setActive(teamIndex);
        events.add(new BattleEvent(EventType.SWITCH, side,
                side + " sent out " + battle.side(side).active().name() + "!", teamIndex));
        battle.pendingReplacements().remove(side);
        if (battle.pendingReplacements().isEmpty()) battle.setStatus(BattleStatus.AWAITING_ACTIONS);
        return events;
    }

    // ------------------------------------------------------------------ validation

    private void requireStatus(Battle battle, BattleStatus expected) {
        if (battle.status() != expected) {
            throw new IllegalActionException("Battle is " + battle.status() + ", expected " + expected);
        }
    }

    private void validate(Battle battle, Side side, Action action) {
        if (action == null) throw new IllegalActionException(side + " must submit an action");
        BattleSide bs = battle.side(side);
        switch (action) {
            case Action.UseMove m -> {
                BattlePokemon p = bs.active();
                if (m.moveIndex() < 0 || m.moveIndex() >= p.moves().size()) {
                    throw new IllegalActionException("Invalid move index " + m.moveIndex());
                }
                if (p.hasAnyPp() && p.moves().get(m.moveIndex()).pp() <= 0) {
                    throw new IllegalActionException(p.moves().get(m.moveIndex()).move().name() + " has no PP left");
                }
            }
            case Action.Switch s -> validateSwitchTarget(bs, s.teamIndex());
        }
    }

    private void validateSwitchTarget(BattleSide bs, int index) {
        if (index < 0 || index >= bs.team().size()) throw new IllegalActionException("Invalid team index " + index);
        if (index == bs.activeIndex() && !bs.active().isFainted()) {
            throw new IllegalActionException("That Pokemon is already in battle");
        }
        if (bs.team().get(index).isFainted()) throw new IllegalActionException("That Pokemon has fainted");
    }

    // ------------------------------------------------------------------ turn order

    private Queued enqueue(Battle battle, Side side, Action action) {
        BattlePokemon active = battle.side(side).active();
        int priority = switch (action) {
            case Action.Switch s -> SWITCH_PRIORITY;
            case Action.UseMove m -> moveFor(active, m.moveIndex()).priority();
        };
        return new Queued(side, action, priority, effectiveSpeed(active));
    }

    private List<Queued> executionOrder(Queued a, Queued b) {
        boolean aFirst;
        if (a.priority() != b.priority()) aFirst = a.priority() > b.priority();
        else if (a.speed() != b.speed()) aFirst = a.speed() > b.speed();
        else aFirst = rng.nextInt(2) == 0;
        return aFirst ? List.of(a, b) : List.of(b, a);
    }

    private int effectiveSpeed(BattlePokemon p) {
        int speed = p.stats().speed();
        if (p.status() == StatusCondition.PARALYSIS) speed = (int) (speed * rules.paralysisSpeedMultiplier());
        return speed;
    }

    // ------------------------------------------------------------------ actions

    private void switchTo(Battle battle, Side side, int index, List<BattleEvent> events) {
        BattleSide bs = battle.side(side);
        BattlePokemon out = bs.active();
        out.resetToxic();
        bs.setActive(index);
        events.add(new BattleEvent(EventType.SWITCH, side,
                side + " withdrew " + out.name() + " and sent out " + bs.active().name() + "!", index));
    }

    /** Null slot means every move is out of PP, so the user Struggles. */
    private MoveSlot pick(BattlePokemon p, int index) {
        return p.hasAnyPp() ? p.moves().get(index) : null;
    }

    private Move moveFor(BattlePokemon p, int index) {
        MoveSlot slot = pick(p, index);
        return slot == null ? Move.struggle() : slot.move();
    }

    private void useMove(Battle battle, Side side, int index, List<BattleEvent> events) {
        BattlePokemon user = battle.side(side).active();
        BattlePokemon target = battle.side(side.opponent()).active();

        if (!canAct(user, side, events)) return;

        MoveSlot slot = pick(user, index);
        Move move = slot == null ? Move.struggle() : slot.move();
        if (slot != null) slot.usePp();
        events.add(new BattleEvent(EventType.MOVE_USED, side, side + "'s " + user.name() + " used " + move.name() + "!", 0));

        if (!accuracyCheck(move)) {
            events.add(new BattleEvent(EventType.MISSED, side, side + "'s " + user.name() + "'s attack missed!", 0));
            return;
        }
        if (move.isDamaging()) dealDamage(side, user, target, move, events);
        else applyStatusMove(side.opponent(), target, move, events);
    }

    /** Sleep, freeze and paralysis can stop a Pokemon from acting. */
    private boolean canAct(BattlePokemon user, Side side, List<BattleEvent> events) {
        switch (user.status()) {
            case SLEEP -> {
                if (user.sleepTurns() > 0) {
                    user.decrementSleep();
                    events.add(new BattleEvent(EventType.CANT_MOVE, side, side + "'s " + user.name() + " is fast asleep.", 0));
                    return false;
                }
                user.setStatus(StatusCondition.NONE);
                events.add(new BattleEvent(EventType.WOKE_UP, side, side + "'s " + user.name() + " woke up!", 0));
            }
            case FREEZE -> {
                if (rng.nextInt(100) < rules.thawChancePercent()) {
                    user.setStatus(StatusCondition.NONE);
                    events.add(new BattleEvent(EventType.THAWED, side, side + "'s " + user.name() + " thawed out!", 0));
                } else {
                    events.add(new BattleEvent(EventType.CANT_MOVE, side, side + "'s " + user.name() + " is frozen solid!", 0));
                    return false;
                }
            }
            case PARALYSIS -> {
                if (rng.nextInt(100) < rules.fullParalysisChancePercent()) {
                    events.add(new BattleEvent(EventType.CANT_MOVE, side, side + "'s " + user.name() + " is fully paralyzed!", 0));
                    return false;
                }
            }
            default -> { }
        }
        return true;
    }

    /** Draws no random number when the move cannot miss (accuracy null or 100). */
    private boolean accuracyCheck(Move move) {
        return move.accuracy() == null || move.accuracy() >= 100 || rng.nextInt(100) < move.accuracy();
    }

    private void dealDamage(Side userSide, BattlePokemon user, BattlePokemon target, Move move, List<BattleEvent> events) {
        Side targetSide = userSide.opponent();
        double effectiveness = calculator.effectiveness(move, target);
        if (effectiveness == 0) {
            events.add(new BattleEvent(EventType.NO_EFFECT, targetSide, "It doesn't affect " + target.name() + "...", 0));
            return;
        }
        boolean crit = rng.nextInt(rules.criticalHitDenominator()) == 0;
        int roll = 85 + rng.nextInt(16);
        int dealt = target.takeDamage(calculator.calculate(user, target, move, crit, roll));

        events.add(new BattleEvent(EventType.DAMAGE, targetSide, target.name() + " lost " + dealt + " HP.", dealt));
        if (crit) events.add(new BattleEvent(EventType.CRITICAL_HIT, targetSide, "A critical hit!", 0));
        if (effectiveness > 1) events.add(new BattleEvent(EventType.SUPER_EFFECTIVE, targetSide, "It's super effective!", 0));
        if (effectiveness < 1) events.add(new BattleEvent(EventType.NOT_VERY_EFFECTIVE, targetSide, "It's not very effective...", 0));
        if (target.isFainted()) events.add(faintEvent(targetSide, target));

        if (!target.isFainted() && move.ailment() != StatusCondition.NONE && move.ailmentChance() > 0
                && rules.canBeAfflicted(target, move.ailment())
                && rng.nextInt(100) < move.ailmentChance()) {
            inflict(targetSide, target, move.ailment(), events);
        }
        if (move.recoilDivisor() > 0 && dealt > 0) {
            int recoil = user.takeDamage(Math.max(1, dealt / move.recoilDivisor()));
            events.add(new BattleEvent(EventType.RECOIL, userSide, user.name() + " is hit with recoil!", recoil));
            if (user.isFainted()) events.add(faintEvent(userSide, user));
        }
    }

    private void applyStatusMove(Side targetSide, BattlePokemon target, Move move, List<BattleEvent> events) {
        if (move.ailment() == StatusCondition.NONE || !rules.canBeAfflicted(target, move.ailment())) {
            events.add(new BattleEvent(EventType.FAILED, targetSide, "But it failed!", 0));
            return;
        }
        inflict(targetSide, target, move.ailment(), events);
    }

    private void inflict(Side side, BattlePokemon target, StatusCondition status, List<BattleEvent> events) {
        target.setStatus(status);
        if (status == StatusCondition.SLEEP) {
            int range = rules.maxSleepTurns() - rules.minSleepTurns() + 1;
            target.setSleepTurns(rules.minSleepTurns() + rng.nextInt(range));
        }
        events.add(new BattleEvent(EventType.STATUS_INFLICTED, side, target.name() + " was afflicted: " + status + ".", 0));
    }

    // ------------------------------------------------------------------ end of turn

    private void applyResidualDamage(Battle battle, List<BattleEvent> events) {
        List<Side> affected = new ArrayList<>();
        for (Side s : Side.values()) {
            BattlePokemon p = battle.side(s).active();
            if (!p.isFainted() && switch (p.status()) {
                case BURN, POISON, BADLY_POISONED -> true;
                default -> false;
            }) affected.add(s);
        }
        if (affected.size() == 2 && !goesFirst(battle.side(Side.P1).active(), battle.side(Side.P2).active())) {
            Collections.reverse(affected);
        }
        for (Side s : affected) {
            BattlePokemon p = battle.side(s).active();
            if (p.status() == StatusCondition.BADLY_POISONED) p.incrementToxic();
            int dealt = p.takeDamage(rules.residualDamage(p));
            events.add(new BattleEvent(EventType.RESIDUAL_DAMAGE, s, p.name() + " is hurt by its " + p.status() + "!", dealt));
            if (p.isFainted()) events.add(faintEvent(s, p));
        }
    }

    private boolean goesFirst(BattlePokemon a, BattlePokemon b) {
        int sa = effectiveSpeed(a), sb = effectiveSpeed(b);
        return sa != sb ? sa > sb : rng.nextInt(2) == 0;
    }

    private BattleEvent faintEvent(Side side, BattlePokemon p) {
        return new BattleEvent(EventType.FAINTED, side, p.name() + " fainted!", 0);
    }

    /** Decide what happens next: battle over, replacements needed, or a fresh turn. */
    private void settle(Battle battle, List<BattleEvent> events) {
        boolean p1Out = !battle.side(Side.P1).hasUsablePokemon();
        boolean p2Out = !battle.side(Side.P2).hasUsablePokemon();
        if (p1Out || p2Out) {
            Side winner = p1Out && p2Out ? null : (p1Out ? Side.P2 : Side.P1);
            battle.finish(winner);
            events.add(new BattleEvent(EventType.BATTLE_END, winner,
                    winner == null ? "The battle ended in a draw." : winner + " wins the battle!", 0));
            return;
        }
        for (Side s : Side.values()) {
            if (battle.side(s).active().isFainted()) battle.pendingReplacements().add(s);
        }
        battle.setStatus(battle.pendingReplacements().isEmpty()
                ? BattleStatus.AWAITING_ACTIONS : BattleStatus.AWAITING_REPLACEMENT);
    }
}
