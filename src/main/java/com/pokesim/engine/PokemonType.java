package com.pokesim.engine;

/** All types across generations. Which ones are "live" is decided by the Ruleset's type chart. */
public enum PokemonType {
    NORMAL, FIRE, WATER, ELECTRIC, GRASS, ICE, FIGHTING, POISON, GROUND, FLYING,
    PSYCHIC, BUG, ROCK, GHOST, DRAGON, DARK, STEEL, FAIRY,
    /** The "???" type used by Struggle: no STAB, never resisted, never immune. */
    TYPELESS
}
