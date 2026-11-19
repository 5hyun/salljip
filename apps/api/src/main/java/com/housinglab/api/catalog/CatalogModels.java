package com.housinglab.api.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CatalogModels {
  private CatalogModels() {}

  public record Region(String regionCode, String name, String parentName) {}

  public record Building(
      String id, String regionCode, String name, String address,
      double longitude, double latitude, int households, int builtYear, String propertyType) {
    /** 유형을 모르는 원천(서울시 공동주택, 데모)은 아파트로 본다. */
    public Building(String id, String regionCode, String name, String address,
        double longitude, double latitude, int households, int builtYear) {
      this(id, regionCode, name, address, longitude, latitude, households, builtYear, "APT");
    }
  }

  public record Trade(
      String id, String buildingId, LocalDate contractDate, BigDecimal areaM2,
      long priceKrw, long monthlyRentKrw, int floor, String transactionType,
      boolean canceled, String sourceId) {}

  public record MapListing(
      Building building,
      Trade latestTrade,
      Trade previousTrade,
      int observedTradeCount,
      String dataAsOf,
      String sourceStatus) {}

  public record PriceRange(long min, long median, long max) {}

  public record Candidate(
      Building building,
      int observedTradeCount,
      PriceRange historicalPriceRange,
      PriceRange currentComparablePriceRange,
      BigDecimal matchedAreaMin,
      BigDecimal matchedAreaMax,
      LocalDate lastTradeDate,
      String confidence,
      List<String> exclusions,
      BigDecimal changeRatePercent) {}
}
