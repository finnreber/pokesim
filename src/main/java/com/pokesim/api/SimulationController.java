package com.pokesim.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import com.pokesim.api.dto.SimulationRequest;
import com.pokesim.api.dto.SimulationResult;
import com.pokesim.service.SimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public (no login): runs a complete random battle so anyone can see the engine work. */
@Tag(name = "Demo", description = "No login needed")
@RestController
@RequestMapping("/api/v1/simulate")
public class SimulationController {
    private final SimulationService simulation;

    public SimulationController(SimulationService simulation) { this.simulation = simulation; }

    @Operation(summary = "Run a complete random battle",
            description = "Two random legal teams fight to the end. Optional body: {\"seed\": 42, \"teamSize\": 3}. The same seed always gives the same battle.")
    @PostMapping
    public SimulationResult simulate(@Valid @RequestBody(required = false) SimulationRequest request) {
        return request == null ? simulation.simulate(null, null) : simulation.simulate(request.seed(), request.teamSize());
    }
}
