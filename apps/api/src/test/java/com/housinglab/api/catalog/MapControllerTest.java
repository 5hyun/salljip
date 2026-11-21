package com.housinglab.api.catalog;

import static com.housinglab.api.catalog.CatalogModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapControllerTest {
  private static final String SANGGYE = "00000000-0000-0000-0000-000000000001";
  private static final String JUNGGYE = "00000000-0000-0000-0000-000000000002";

  /** DB 없이 컨트롤러 로직만 검증하기 위한 고정 응답 카탈로그. */
  private static final class FakeCatalog extends JdbcCatalog {
    FakeCatalog() { super(null); }

    @Override
    public List<Building> buildings(Collection<String> regionCodes) {
      return List.of(
          new Building(SANGGYE, "11350", "상계주공", "서울특별시 노원구 상계동 1", 127.0607, 37.6673, 0, 1988, "APT"),
          new Building(JUNGGYE, "11350", "중계주공", "서울특별시 노원구 중계동 2", 127.0748, 37.6519, 0, 1992, "APT"));
    }

    @Override
    public Map<String, BuildingSales> latestSales(
        String regionCode, BigDecimal areaMin, BigDecimal areaMax, long priceMin, long priceMax) {
      var latest = trade("t2", SANGGYE, "2026-10-03", 735_000_000L);
      var previous = trade("t1", SANGGYE, "2026-08-11", 710_000_000L);
      return Map.of(SANGGYE, new BuildingSales(latest, previous, 2));
    }

    @Override
    public Instant dataAsOf() { return Instant.parse("2026-11-17T00:00:00Z"); }

    private static Trade trade(String id, String building, String date, long price) {
      return new Trade(id, building, LocalDate.parse(date), new BigDecimal("49.94"), price, 0L, 5, "SALE", false, "MOLIT_APT");
    }
  }

  private final MapController controller = new MapController(new FakeCatalog());

  @Test
  void returnsRealSaleListingsWithLatestAndPreviousTrade() {
    var listings = controller.apartments(
        "11350", "SALE", 0L, Long.MAX_VALUE,
        new BigDecimal("40"), new BigDecimal("85"), false, null);

    assertThat(listings).singleElement().satisfies(listing -> {
      assertThat(listing.building().name()).isEqualTo("상계주공");
      assertThat(listing.latestTrade().priceKrw()).isEqualTo(735_000_000L);
      assertThat(listing.previousTrade().priceKrw()).isEqualTo(710_000_000L);
      assertThat(listing.observedTradeCount()).isEqualTo(2);
      assertThat(listing.sourceStatus()).isEqualTo("PUBLIC_ALLOWED");
      assertThat(listing.dataAsOf()).isEqualTo("2026-11-17");
    });
  }

  @Test
  void keepsBuildingsWithoutMatchingTradesWhenRequested() {
    var listings = controller.apartments(
        "11350", "SALE", 0L, Long.MAX_VALUE,
        BigDecimal.ZERO, new BigDecimal("300"), true, null);

    assertThat(listings).hasSize(2);
    assertThat(listings.getFirst().latestTrade()).isNotNull();
    assertThat(listings.getLast().latestTrade()).isNull();
    assertThat(listings.getLast().sourceStatus()).isEqualTo("NO_MATCHING_TRADE");
  }

  @Test
  void rentalTabsShowBuildingsWithoutPricesUntilRentalsAreIngested() {
    var listings = controller.apartments(
        "11350", "JEONSE", 0L, Long.MAX_VALUE,
        BigDecimal.ZERO, new BigDecimal("300"), true, null);

    assertThat(listings).hasSize(2).allSatisfy(listing -> assertThat(listing.latestTrade()).isNull());
  }

  @Test
  void appliesViewportBounds() {
    var listings = controller.apartments(
        "11350", "SALE", 0L, Long.MAX_VALUE,
        BigDecimal.ZERO, new BigDecimal("300"), true, "127.05,37.66,127.07,37.68");

    assertThat(listings).extracting(listing -> listing.building().name()).containsExactly("상계주공");
  }

  @Test
  void rejectsUnsupportedTransactionType() {
    assertThatThrownBy(() -> controller.apartments(
        "11350", "UNKNOWN", 0L, Long.MAX_VALUE,
        BigDecimal.ZERO, new BigDecimal("300"), false, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
