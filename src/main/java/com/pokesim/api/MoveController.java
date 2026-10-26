package com.pokesim.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.pokesim.api.dto.MoveResponse;
import com.pokesim.api.dto.PageResponse;
import com.pokesim.service.DexService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dex", description = "Moves (public, read-only)")
@RestController
@RequestMapping("/api/v1/moves")
public class MoveController {
    private final DexService dex;

    public MoveController(DexService dex) { this.dex = dex; }

    @GetMapping
    public PageResponse<MoveResponse> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return dex.listMoves(type, name, page, size);
    }

    @GetMapping("/{id}")
    public MoveResponse get(@PathVariable int id) {
        return dex.getMove(id);
    }
}
