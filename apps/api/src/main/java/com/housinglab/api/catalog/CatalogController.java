package com.housinglab.api.catalog;

import static com.housinglab.api.catalog.CatalogModels.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
  private final JdbcCatalog catalog;

  public CatalogController(JdbcCatalog catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/regions")
  List<Region> regions(@RequestParam(defaultValue = "") String query) {
    return catalog.regions().stream().filter(region -> region.name().contains(query)).toList();
  }

  @GetMapping("/buildings")
  List<Building> buildings(
      @RequestParam(required = false) String bbox,
      @RequestParam(required = false) String regionCode,
      @RequestParam(defaultValue = "12") int zoom) {
    var bounds = parseBounds(bbox);
    return catalog.buildings(regionCode == null ? List.of() : List.of(regionCode)).stream()
        .filter(building -> bounds == null || withinBounds(building, bounds))
        .toList();
  }

  @GetMapping("/trades")
  List<Trade> trades(
      @RequestParam String buildingId,
      @RequestParam(required = false) String transactionType,
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(required = false) BigDecimal areaMin) {
    if (!JdbcCatalog.isDatabaseId(buildingId)) return List.of();
    // 실거래는 매매만 수집한다.
    if (transactionType != null && !"SALE".equalsIgnoreCase(transactionType)) return List.of();
    return catalog.tradesForBuilding(buildingId, from, to, areaMin);
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
