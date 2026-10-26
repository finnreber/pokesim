package com.pokesim.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;
import com.pokesim.api.dto.PageResponse;
import com.pokesim.api.dto.TeamRequest;
import com.pokesim.api.dto.TeamResponse;
import com.pokesim.service.TeamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Every route here requires a valid token; the user id always comes from the token, never from the request. */
@Tag(name = "Teams", description = "Build and manage your teams (max 6 Pokemon, 4 moves each, legal FireRed moves only)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {
    private final TeamService teams;

    public TeamController(TeamService teams) { this.teams = teams; }

    @PostMapping
    public ResponseEntity<TeamResponse> create(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TeamRequest request) {
        TeamResponse created = teams.create(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/teams/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<TeamResponse> list(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return teams.list(userId(jwt), page, size);
    }

    @GetMapping("/{id}")
    public TeamResponse get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return teams.get(userId(jwt), id);
    }

    @PutMapping("/{id}")
    public TeamResponse update(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody TeamRequest request) {
        return teams.update(userId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        teams.delete(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
