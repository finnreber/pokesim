package com.pokesim.engine;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Test double: returns exactly the numbers you script, in order. It FAILS if the engine draws a
 * number you did not expect, so a test also pins down how much randomness a mechanic consumes.
 *
 * Engine draw order for a damaging move: [accuracy if < 100], crit (0 = crit), damage roll (85 + n),
 * [secondary-effect chance if applicable]. So {15, 15} means "no crit, maximum roll (100%)".
 */
final class ScriptedRandom implements RandomSource {
    private final Deque<Integer> values = new ArrayDeque<>();

    ScriptedRandom(int... scripted) {
        for (int v : scripted) values.add(v);
    }

    @Override
    public int nextInt(int bound) {
        if (values.isEmpty()) throw new AssertionError("Unexpected RNG draw with bound " + bound);
        int v = values.poll();
        if (v < 0 || v >= bound) throw new AssertionError("Scripted value " + v + " out of range for bound " + bound);
        return v;
    }

    void assertAllUsed() {
        if (!values.isEmpty()) throw new AssertionError("Unused scripted RNG values: " + values);
    }
}
