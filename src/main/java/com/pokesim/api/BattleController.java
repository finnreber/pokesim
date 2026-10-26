package com.pokesim.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import com.pokesim.api.dto.ActionRequest;
import com.pokesim.api.dto.ActionResponse;
import com.pokesim.api.dto.BattleSummary;
import com.pokesim.api.dto.BattleView;
import com.pokesim.api.dto.CreateBattleRequest;
import com.pokesim.api.dto.EventDto;
import com.pokesim.api.dto.JoinBattleRequest;
import com.pokesim.api.dto.LobbySummary;
import com.pokesim.api.dto.PageResponse;
import com.pokesim.service.BattleService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Battles", description = "Host or join a lobby, then submit one action per turn")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/battles")
public class BattleController {
    private final BattleService battles;

    public BattleController(BattleService battles) { this.battles = battles; }

    @PostMapping
    public ResponseEntity<BattleView> create(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBattleRequest request) {
        BattleView created = battles.create(userId(jwt), request.teamId());
        return ResponseEntity.created(URI.create("/api/v1/battles/" + created.id())).body(created);
    }

    /** Battles I am in. */
    @GetMapping
    public PageResponse<BattleSummary> mine(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return battles.mine(userId(jwt), page, size);
    }

    /** Lobbies hosted by other players that are waiting for an opponent. */
    @GetMapping("/open")
    public PageResponse<LobbySummary> open(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return battles.openLobbies(userId(jwt), page, size);
    }

    @GetMapping("/{id}")
    public BattleView get(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return battles.get(userId(jwt), id);
    }

    @PostMapping("/{id}/join")
    public BattleView join(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody JoinBattleRequest request) {
        return battles.join(userId(jwt), id, request.teamId());
    }

    @Operation(summary = "Submit your action for this turn",
            description = "type MOVE with the move slot (0-3), or SWITCH with the team slot (0-5). The turn resolves when both players have chosen. After a faint, send SWITCH to pick the replacement.")
    @PostMapping("/{id}/actions")
    public ActionResponse act(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ActionRequest request) {
        return battles.submit(userId(jwt), id, request);
    }

    @GetMapping("/{id}/events")
    public PageResponse<EventDto> events(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return battles.events(userId(jwt), id, page, size);
    }

    @Operation(summary = "Give up: your opponent wins")
    @PostMapping("/{id}/forfeit")
    public BattleView forfeit(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return battles.forfeit(userId(jwt), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        battles.cancel(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
