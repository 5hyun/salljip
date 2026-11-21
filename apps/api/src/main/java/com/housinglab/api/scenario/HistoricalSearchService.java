package com.housinglab.api.scenario;

import static com.housinglab.api.catalog.CatalogModels.*;
import static com.housinglab.api.scenario.HistoricalSearchModels.*;

import com.housinglab.api.catalog.JdbcCatalog;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class HistoricalSearchService {
  private static final String VERSION = "historical-options-1.0.0";
  private final JdbcCatalog catalog;

  public HistoricalSearchService(JdbcCatalog catalog) {
    this.catalog = catalog;
  }

  public Response search(Request request) {
    validate(request);
    long budget = Math.addExact(request.equityKrw(), request.assumedLoanKrw());
    var buildings = catalog.buildings(request.regionCodes());
    var sales = catalog.sales(request.regionCodes(), request.period().from(), request.areaM2().min(), request.areaM2().max());
    // 과거 기간 시작 이후의 유사면적 매매를 한 번에 읽어 건물별로 묶는다(실데이터는 수만 건).
    Map<String, List<Trade>> salesByBuilding = sales.stream()
        .sorted(Comparator.comparingLong(Trade::priceKrw))
        .collect(Collectors.groupingBy(Trade::buildingId));
    var candidates = new ArrayList<Candidate>();

    for (var building : buildings) {
      var trades = salesByBuilding.getOrDefault(building.id(), List.of());
      var historical = trades.stream().filter(trade -> !trade.contractDate().isAfter(request.period().to())).toList();
      if (historical.isEmpty()) continue;
      long medianHistorical = median(historical);
      if (medianHistorical + request.acquisitionCostKrw() > budget) continue;
      var current = trades.stream().filter(trade -> trade.contractDate().isAfter(request.period().to())).toList();
      var currentRange = current.isEmpty() ? null : range(current);
      var change = currentRange == null ? null : BigDecimal.valueOf(currentRange.median() - medianHistorical)
          .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(medianHistorical), 2, RoundingMode.HALF_UP);
      var exclusions = new ArrayList<String>();
      if (current.isEmpty()) exclusions.add("최근 유사면적 거래 표본 없음");
      if (historical.size() < 2) exclusions.add("과거 거래 표본 부족");
      candidates.add(new Candidate(
          building, historical.size(), range(historical), currentRange,
          historical.stream().map(Trade::areaM2).min(BigDecimal::compareTo).orElseThrow(),
          historical.stream().map(Trade::areaM2).max(BigDecimal::compareTo).orElseThrow(),
          current.stream().map(Trade::contractDate).max(LocalDate::compareTo).orElse(historical.get(historical.size() - 1).contractDate()),
          historical.size() >= 2 && !current.isEmpty() ? "HIGH" : "LOW", exclusions, change));
    }

    candidates.sort(comparator(request.sort()));
    var limited = candidates.stream().limit(request.limit()).toList();
    var changes = candidates.stream().map(Candidate::changeRatePercent).filter(value -> value != null).sorted().toList();
    var distribution = new Distribution(
        candidates.size(),
        (int) changes.stream().filter(value -> value.signum() > 0).count(),
        (int) changes.stream().filter(value -> value.signum() < 0).count(),
        changes.isEmpty() ? BigDecimal.ZERO : changes.get(changes.size() / 2));
    var warnings = new ArrayList<String>();
    warnings.add("국토교통부 신고 실거래 관측치입니다. 당시 매물 존재나 대출 승인을 보장하지 않습니다.");
    if (candidates.size() > limited.size()) warnings.add("조건에 맞는 " + candidates.size() + "곳 중 " + limited.size() + "곳만 표시합니다.");
    if (limited.isEmpty()) warnings.add("조건에 맞는 관측 거래가 없습니다. 기간·예산·면적을 넓혀 보세요.");
    return new Response(
        limited,
        Map.of("budgetKrw", budget, "acquisitionCostKrw", request.acquisitionCostKrw(), "loan", "사용자 입력 가정"),
        catalog.dataAsOf(),
        catalog.sources(),
        warnings, null, VERSION, distribution);
  }

  private void validate(Request request) {
    if (request.period().from().isAfter(request.period().to())) throw new IllegalArgumentException("시작일은 종료일보다 앞서야 합니다.");
    if (request.areaM2().min().compareTo(request.areaM2().max()) > 0) throw new IllegalArgumentException("최소 면적은 최대 면적보다 작아야 합니다.");
  }

  private PriceRange range(List<Trade> trades) {
    return new PriceRange(trades.getFirst().priceKrw(), median(trades), trades.getLast().priceKrw());
  }

  private long median(List<Trade> trades) { return trades.get(trades.size() / 2).priceKrw(); }

  private Comparator<Candidate> comparator(String sort) {
    if ("changeDesc".equals(sort)) return Comparator.comparing(Candidate::changeRatePercent, Comparator.nullsLast(Comparator.reverseOrder()));
    if ("currentPriceAsc".equals(sort)) return Comparator.comparing(candidate -> candidate.currentComparablePriceRange() == null ? Long.MAX_VALUE : candidate.currentComparablePriceRange().median());
    return Comparator.comparingLong(candidate -> candidate.historicalPriceRange().median());
  }
}
