package com.housinglab.api.catalog;

import static com.housinglab.api.catalog.CatalogModels.*;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/map")
public class MapController {
  private final JdbcCatalog catalog;

  public MapController(JdbcCatalog catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/apartments")
  List<MapListing> apartments(
      @RequestParam(defaultValue = "11350") String regionCode,
      @RequestParam(defaultValue = "SALE") String transactionType,
      @RequestParam(defaultValue = "0") long priceMin,
      @RequestParam(defaultValue = "9223372036854775807") long priceMax,
      @RequestParam(defaultValue = "0") BigDecimal areaMin,
      @RequestParam(defaultValue = "300") BigDecimal areaMax,
      @RequestParam(defaultValue = "true") boolean includeUnpriced,
      @RequestParam(required = false) String bbox) {
    String normalizedType = transactionType.toUpperCase(Locale.ROOT);
    if (!List.of("SALE", "JEONSE", "MONTHLY").contains(normalizedType)) {
      throw new IllegalArgumentException("거래유형은 SALE, JEONSE, MONTHLY 중 하나여야 합니다.");
    }
    if (priceMin > priceMax) throw new IllegalArgumentException("최소 가격은 최대 가격보다 작아야 합니다.");
    if (areaMin.compareTo(areaMax) > 0) throw new IllegalArgumentException("최소 면적은 최대 면적보다 작아야 합니다.");

    return listings(regionCode, normalizedType, priceMin, priceMax, areaMin, areaMax, includeUnpriced, parseBounds(bbox));
  }

  /** 국토교통부 매매 실거래(PostGIS). 전월세는 아직 수집하지 않아 가격 없는 건물로만 표시된다. */
  private List<MapListing> listings(
      String regionCode, String type, long priceMin, long priceMax,
      BigDecimal areaMin, BigDecimal areaMax, boolean includeUnpriced, double[] bounds) {
    var sales = "SALE".equals(type)
        ? catalog.latestSales(regionCode, areaMin, areaMax, priceMin, priceMax)
        : Map.<String, JdbcCatalog.BuildingSales>of();
    String asOf = catalog.dataAsOf().atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toString();
    return catalog.buildings(List.of(regionCode)).stream()
        .filter(building -> bounds == null || withinBounds(building, bounds))
        .map(building -> {
          var sale = sales.get(building.id());
          if (sale == null) {
            return includeUnpriced ? new MapListing(building, null, null, 0, asOf, "NO_MATCHING_TRADE") : null;
          }
          return new MapListing(building, sale.latest(), sale.previous(), sale.count(), asOf, "PUBLIC_ALLOWED");
        })
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing((MapListing listing) -> listing.latestTrade() == null))
        .toList();
  }

  private double[] parseBounds(String bbox) {
    if (bbox == null || bbox.isBlank()) return null;
    var parts = bbox.split(",");
    if (parts.length != 4) throw new IllegalArgumentException("bbox는 west,south,east,north 형식이어야 합니다.");
    try {
      return new double[] {
          Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
          Double.parseDouble(parts[2]), Double.parseDouble(parts[3])};
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("bbox 좌표는 숫자여야 합니다.");
    }
  }

  private boolean withinBounds(Building building, double[] bounds) {
    return building.longitude() >= bounds[0] && building.longitude() <= bounds[2]
        && building.latitude() >= bounds[1] && building.latitude() <= bounds[3];
  }
}
