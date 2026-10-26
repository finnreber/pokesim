package com.pokesim.api;

import com.pokesim.api.dto.SimulationRequest;
import com.pokesim.api.dto.SimulationResult;
import com.pokesim.service.SimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public (no login): runs a complete random battle so anyone can see the engine work. */
@RestController
@RequestMapping("/api/v1/simulate")
public class SimulationController {
    private final SimulationService simulation;

    public SimulationController(SimulationService simulation) { this.simulation = simulation; }

    @PostMapping
    public SimulationResult simulate(@Valid @RequestBody(required = false) SimulationRequest request) {
        return request == null ? simulation.simulate(null, null) : simulation.simulate(request.seed(), request.teamSize());
    }
}
