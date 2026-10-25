package com.pokesim.api.dto;

public record MoveResponse(int id, String name, String type, String category, int power, Integer accuracy,
                           int pp, int priority, String ailment, int ailmentChance, int recoilDivisor) { }
