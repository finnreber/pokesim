package com.pokesim.engine;

/** One line of the battle log. {@code side} is who the event happened to; {@code value} is e.g. HP lost. */
public record BattleEvent(EventType type, Side side, String message, int value) {}
