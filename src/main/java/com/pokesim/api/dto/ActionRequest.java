package com.pokesim.api.dto;

import com.pokesim.engine.ActionKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** {"type":"MOVE","index":0} uses the active Pokemon's first move; {"type":"SWITCH","index":2} sends out team slot 2. */
public record ActionRequest(@NotNull ActionKind type, @Min(0) int index) { }
