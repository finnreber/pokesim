package com.pokesim.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateBattleRequest(@NotNull UUID teamId) { }
