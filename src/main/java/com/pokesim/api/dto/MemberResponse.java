package com.pokesim.api.dto;

import java.util.List;

public record MemberResponse(int dexNumber, String species, int level, List<MoveRef> moves) { }
