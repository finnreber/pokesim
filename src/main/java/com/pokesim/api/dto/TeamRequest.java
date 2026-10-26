package com.pokesim.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TeamRequest(
        @NotBlank @Size(max = 40) String name,
        @NotNull @Size(min = 1, max = 6) List<@Valid @NotNull MemberRequest> members) { }
