package com.pokesim.api.dto;

import java.time.Instant;
import java.util.UUID;

public record BattleSummary(UUID id, String phase, String opponent, boolean awaitingYourAction, Instant updatedAt) { }
