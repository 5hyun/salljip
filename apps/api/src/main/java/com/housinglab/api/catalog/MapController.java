package com.housinglab.api.catalog;

import static com.housinglab.api.catalog.CatalogModels.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/map")
public class MapController {
  private final DemoCatalog catalog;
  private final ApartmentCatalogService apartmentCatalog;
  /** 실데이터 모드(housing-lab.demo-mode=false)에서만 존재한다. */
  private final JdbcCatalog realCatalog;

  public MapController(DemoCatalog catalog, ApartmentCatalogService apartmentCatalog) {
    this(catalog, apartmentCatalog, Optional.empty());
  }

  @Autowired
  public MapController(DemoCatalog catalog, ApartmentCatalogService apartmentCatalog, Optional<JdbcCatalog> realCatalog) {
    this.catalog = catalog;
    this.apartmentCatalog = apartmentCatalog;
    this.realCatalog = realCatalog.orElse(null);
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

    var bounds = parseBounds(bbox);
    if (realCatalog != null) return realListings(regionCode, normalizedType, priceMin, priceMax, areaMin, areaMax, includeUnpriced, bounds);
    return apartmentCatalog.buildings(regionCode).stream()
        .filter(building -> bounds == null || withinBounds(building, bounds))
        .map(building -> listing(building, normalizedType, priceMin, priceMax, areaMin, areaMax, includeUnpriced))
        .filter(listing -> listing != null)
        .sorted(Comparator.comparing((MapListing listing) -> listing.latestTrade() == null))
        .toList();
  }

  /** 국토교통부 매매 실거래(PostGIS). 전월세는 아직 수집하지 않아 가격 없는 건물로만 표시된다. */
  private List<MapListing> realListings(
      String regionCode, String type, long priceMin, long priceMax,
      BigDecimal areaMin, BigDecimal areaMax, boolean includeUnpriced, double[] bounds) {
    var sales = "SALE".equals(type)
        ? realCatalog.latestSales(regionCode, areaMin, areaMax, priceMin, priceMax)
        : java.util.Map.<String, JdbcCatalog.BuildingSales>of();
    String asOf = realCatalog.dataAsOf().atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate().toString();
    return realCatalog.buildings(List.of(regionCode)).stream()
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

  private MapListing listing(
      Building building,
      String type,
      long priceMin,
      long priceMax,
      BigDecimal areaMin,
      BigDecimal areaMax,
      boolean includeUnpriced) {
    var observations = catalog.trades().stream()
        .filter(trade -> !trade.canceled())
        .filter(trade -> trade.buildingId().equals(building.id()))
        .filter(trade -> trade.transactionType().equals(type))
        .filter(trade -> trade.areaM2().compareTo(areaMin) >= 0 && trade.areaM2().compareTo(areaMax) <= 0)
        .filter(trade -> trade.priceKrw() >= priceMin && trade.priceKrw() <= priceMax)
        .sorted(Comparator.comparing(Trade::contractDate).reversed())
        .toList();
    if (observations.isEmpty()) {
      return includeUnpriced
          ? new MapListing(building, null, null, 0, LocalDate.now().toString(), "NO_MATCHING_TRADE")
          : null;
    }
    var latest = observations.getFirst();
    var previous = observations.size() > 1 ? observations.get(1) : null;
    return new MapListing(building, latest, previous, observations.size(),
        LocalDate.of(2026, 11, 17).toString(), "INTERNAL_TEST");
  }
}
