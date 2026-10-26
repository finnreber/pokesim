package com.pokesim.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "must be 3-20 letters, digits or underscores")
        String username,
        // 72 is BCrypt's input limit: anything longer would be silently truncated
        @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters")
        String password) { }
