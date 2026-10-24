package com.pokesim.engine;

import java.util.Random;

public final class JavaRandomSource implements RandomSource {
    private final Random random;

    public JavaRandomSource(long seed) { this.random = new Random(seed); }

    @Override
    public int nextInt(int bound) { return random.nextInt(bound); }
}
