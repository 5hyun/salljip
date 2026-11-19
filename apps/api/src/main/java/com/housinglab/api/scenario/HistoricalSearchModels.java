package com.housinglab.api.scenario;

import com.housinglab.api.catalog.CatalogModels.Candidate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class HistoricalSearchModels {
  private HistoricalSearchModels() {}
  public record Period(@NotNull LocalDate from, @NotNull LocalDate to) {}
  public record Area(@NotNull @Min(1) BigDecimal min, @NotNull @Max(300) BigDecimal max) {}
  public record Request(
      @Valid @NotNull Period period,
      @NotEmpty List<String> regionCodes,
      @PositiveOrZero long equityKrw,
      @PositiveOrZero long assumedLoanKrw,
      @PositiveOrZero long acquisitionCostKrw,
      @Valid @NotNull Area areaM2,
      String sort,
      @Min(1) @Max(100) int limit,
      String cursor) {}
  public record Response(
      List<Candidate> results,
      Map<String, Object> assumptions,
      Instant dataAsOf,
      List<Map<String, String>> sources,
      List<String> warnings,
      String nextCursor,
      String calculationVersion,
      Distribution distribution) {}
  public record Distribution(int total, int increased, int decreased, BigDecimal medianChangePercent) {}
}
