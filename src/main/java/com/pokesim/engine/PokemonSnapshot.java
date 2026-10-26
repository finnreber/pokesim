package com.pokesim.engine;

import java.util.List;

/** Everything needed to rebuild one Pokemon exactly, so a saved battle never depends on later data changes. */
public record PokemonSnapshot(int dexNumber, String name, List<PokemonType> types, StatBlock baseStats, int level,
                              StatBlock stats, List<MoveState> moves, int currentHp, StatusCondition status,
                              int sleepTurns, int toxicCounter) { }
