package com.housinglab.api.scenario;

import static com.housinglab.api.scenario.HistoricalSearchModels.*;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/historical-options")
public class HistoricalSearchController {
  private final HistoricalSearchService service;
  public HistoricalSearchController(HistoricalSearchService service) { this.service = service; }

  @PostMapping("/search")
  Response search(@Valid @RequestBody Request request) { return service.search(request); }
}
