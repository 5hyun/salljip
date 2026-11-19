package com.housinglab.api.simulation;

import static com.housinglab.api.simulation.SimulationModels.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {
  private final CalculationEngine engine;
  private final Map<String, HousingResult> runs = new ConcurrentHashMap<>();

  public SimulationService(CalculationEngine engine) { this.engine = engine; }

  public HousingResult calculate(HousingRequest request) {
    var result = engine.calculate(request);
    runs.put(result.id(), result);
    return result;
  }

  public HousingResult get(String id) {
    var result = runs.get(id);
    if (result == null) throw new IllegalArgumentException("시뮬레이션 결과를 찾을 수 없습니다: " + id);
    return result;
  }

  public AssetComparison compareAssets(AssetRequest request) {
    var annualRates = Map.of(
        "KOSPI", new BigDecimal("5.4"),
        "NASDAQ", new BigDecimal("12.8"),
        "SP500", new BigDecimal("9.7"),
        "DEPOSIT", new BigDecimal("3.2"),
        "INSTALLMENT", new BigDecimal("3.6"));
    var names = Map.of(
        "KOSPI", "KOSPI 가격지수",
        "NASDAQ", "NASDAQ 종합",
        "SP500", "S&P 500 가격지수",
        "DEPOSIT", "정기예금 벤치마크",
        "INSTALLMENT", "정기적금 벤치마크");
    var results = annualRates.entrySet().stream().map(entry -> {
      double years = request.months() / 12.0;
      long finalValue = BigDecimal.valueOf(request.initialKrw())
          .multiply(BigDecimal.valueOf(Math.pow(1 + entry.getValue().doubleValue() / 100, years)))
          .setScale(0, RoundingMode.HALF_UP).longValueExact();
      var returnPercent = BigDecimal.valueOf(finalValue - request.initialKrw()).multiply(BigDecimal.valueOf(100))
          .divide(BigDecimal.valueOf(request.initialKrw()), 2, RoundingMode.HALF_UP);
      return new AssetResult(entry.getKey(), names.get(entry.getKey()), request.initialKrw(), finalValue, returnPercent,
          entry.getKey().equals("DEPOSIT") || entry.getKey().equals("INSTALLMENT")
              ? "세전 금리 대체 벤치마크"
              : "배당·세금·수수료 제외 가격지수 가정",
          "INTERNAL_TEST");
    }).sorted((a, b) -> b.finalKrw() > a.finalKrw() ? 1 : -1).toList();
    return new AssetComparison(results, Instant.parse("2026-11-17T00:00:00Z"),
        List.of("권리 확인 전 공개 배포가 금지된 합성 수익률입니다.", "미국 지수는 실제 서비스에서 관측일별 환율을 적용해야 합니다."));
  }
}
