package com.pokesim.api.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) { }
