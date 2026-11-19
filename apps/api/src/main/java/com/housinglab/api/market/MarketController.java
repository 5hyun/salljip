package com.housinglab.api.market;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MarketController {
  @GetMapping("/market-indicators")
  List<Map<String, Object>> indicators(@RequestParam String region, @RequestParam(required = false) List<String> codes) {
    return List.of(
        Map.of("code", "JEONSE_PRICE_INDEX", "name", "전세가격지수", "value", 102.4, "unit", "2025.01=100", "period", "2026-10", "releasedAt", LocalDate.parse("2026-11-14"), "source", "DEMO_RONE", "official", true),
        Map.of("code", "JEONSE_TRADE_COUNT", "name", "전월세 신고 거래량", "value", 1284, "unit", "건", "period", "2026-10", "releasedAt", LocalDate.parse("2026-11-10"), "source", "DEMO_MOLIT", "official", true),
        Map.of("code", "MOVE_IN_SUPPLY", "name", "향후 입주 예정 물량", "value", 840, "unit", "세대", "period", "2027", "releasedAt", LocalDate.parse("2026-11-01"), "source", "DEMO_SUPPLY", "official", false));
  }

  @GetMapping("/data-sources/{sourceId}")
  Map<String, Object> source(@PathVariable String sourceId) {
    return Map.of("sourceId", sourceId, "permission", "INTERNAL_TEST", "allowedPublic", false, "allowedCommercial", false,
        "attribution", "합성 데모 데이터", "reviewedAt", "2026-11-17", "warning", "실데이터로 해석하지 마세요.");
  }
}
