package com.pokesim.engine;

import static com.pokesim.engine.PokemonType.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TypeChartTest {
    private final TypeChart chart = TypeChart.gen2to5();

    @Test @DisplayName("super effective, resisted and immune pairs")
    void classicMatchups() {
        assertEquals(2.0, chart.multiplier(WATER, FIRE));
        assertEquals(0.5, chart.multiplier(FIRE, WATER));
        assertEquals(0.0, chart.multiplier(ELECTRIC, GROUND));
        assertEquals(0.0, chart.multiplier(NORMAL, GHOST));
        assertEquals(0.0, chart.multiplier(GHOST, NORMAL));
        assertEquals(0.0, chart.multiplier(GROUND, FLYING));
    }

    @Test @DisplayName("unlisted pairs are neutral")
    void neutralByDefault() {
        assertEquals(1.0, chart.multiplier(NORMAL, FIRE));
        assertEquals(1.0, chart.multiplier(TYPELESS, STEEL));
    }

    @Test @DisplayName("Gen 3 quirk: Steel resists Ghost and Dark (removed in Gen 6)")
    void steelResistsGhostAndDarkInGen3() {
        assertEquals(0.5, chart.multiplier(GHOST, STEEL));
        assertEquals(0.5, chart.multiplier(DARK, STEEL));
    }

    @Test @DisplayName("no Fairy interactions exist yet in this chart")
    void fairyNotInGen3() {
        assertEquals(1.0, chart.multiplier(DRAGON, FAIRY));
        assertEquals(1.0, chart.multiplier(FAIRY, DRAGON));
    }
}
