package com.pokesim.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.pokesim.api.dto.MoveResponse;
import com.pokesim.api.dto.PageResponse;
import com.pokesim.api.dto.SpeciesDetail;
import com.pokesim.api.dto.SpeciesSummary;
import com.pokesim.service.DexService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dex", description = "Species and what they can learn in FireRed (public, read-only)")
@RestController
@RequestMapping("/api/v1/species")
public class SpeciesController {
    private final DexService dex;

    public SpeciesController(DexService dex) { this.dex = dex; }

    @GetMapping
    public PageResponse<SpeciesSummary> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return dex.listSpecies(type, name, page, size);
    }

    @GetMapping("/{dexNumber}")
    public SpeciesDetail get(@PathVariable int dexNumber) {
        return dex.getSpecies(dexNumber);
    }

    @GetMapping("/{dexNumber}/moves")
    public List<MoveResponse> learnset(@PathVariable int dexNumber) {
        return dex.learnset(dexNumber);
    }
}
