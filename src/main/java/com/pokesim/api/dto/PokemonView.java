package com.pokesim.api.dto;

import java.util.List;

/** {@code moves} is only filled in for your own Pokemon: an opponent's moves are hidden information. */
public record PokemonView(String species, List<String> types, int level, int currentHp, int maxHp, String status,
                          boolean fainted, List<MoveView> moves) { }
