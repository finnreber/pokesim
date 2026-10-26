package com.pokesim.api.dto;

import com.pokesim.engine.BattleEvent;
import com.pokesim.persistence.BattleEventEntity;

public record EventDto(int turn, String type, String side, String message, int amount) {
    public static EventDto of(int turn, BattleEvent e) {
        return new EventDto(turn, e.type().name(), e.side() == null ? null : e.side().name(), e.message(), e.value());
    }

    public static EventDto from(BattleEventEntity e) {
        return new EventDto(e.getTurn(), e.getType().name(), e.getSide() == null ? null : e.getSide().name(),
                e.getMessage(), e.getAmount());
    }
}
