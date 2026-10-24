package com.pokesim.engine;

/** Six stats. Used for both base stats (species data) and final computed stats (battle). */
public record StatBlock(int hp, int attack, int defense, int spAttack, int spDefense, int speed) {}
