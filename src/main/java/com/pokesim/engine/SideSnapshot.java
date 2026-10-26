package com.pokesim.engine;

import java.util.List;

public record SideSnapshot(List<PokemonSnapshot> team, int activeIndex) { }
