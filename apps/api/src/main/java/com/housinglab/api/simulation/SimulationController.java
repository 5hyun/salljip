package com.housinglab.api.simulation;

import static com.housinglab.api.simulation.SimulationModels.*;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SimulationController {
  private final SimulationService service;
  public SimulationController(SimulationService service) { this.service = service; }

  @PostMapping("/simulations/housing")
  HousingResult housing(@Valid @RequestBody HousingRequest request) { return service.calculate(request); }

  @GetMapping("/simulations/{id}")
  HousingResult get(@PathVariable String id) { return service.get(id); }

  @PostMapping("/comparisons/asset")
  AssetComparison asset(@Valid @RequestBody AssetRequest request) { return service.compareAssets(request); }
}
