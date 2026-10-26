package com.pokesim.api.dto;

import java.util.List;

public record SideView(String username, int activeIndex, List<PokemonView> team) { }
