package com.pokesim.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TeamResponse(UUID id, String name, List<MemberResponse> members, Instant createdAt, Instant updatedAt) { }
