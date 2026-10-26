package com.pokesim.api.dto;

import java.time.Instant;
import java.util.UUID;

public record LobbySummary(UUID id, String host, Instant createdAt) { }
