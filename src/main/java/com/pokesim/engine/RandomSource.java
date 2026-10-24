package com.pokesim.engine;

/**
 * The engine's ONLY source of randomness. Injecting it makes battles deterministic in tests
 * and replayable in production (store the seed, replay the battle).
 */
public interface RandomSource {
    /** @return an int in [0, bound) */
    int nextInt(int bound);
}
