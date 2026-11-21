package com.housinglab.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.housinglab.api.usage.ApiUsageRecorder;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MapControllerTest {
  private final DemoCatalog catalog = new DemoCatalog();
  private final MapController controller = new MapController(
      catalog, new ApartmentCatalogService(catalog, "", ApiUsageRecorder.disabled()));

  @Test
  void returnsCurrentSaleListingsForNowon() {
    var listings = controller.apartments(
        "11350", "SALE", 0L, Long.MAX_VALUE,
        new BigDecimal("40"), new BigDecimal("85"), false, null);

    assertThat(listings).hasSize(5);
    assertThat(listings).allSatisfy(listing -> {
      assertThat(listing.building().regionCode()).isEqualTo("11350");
      assertThat(listing.latestTrade().transactionType()).isEqualTo("SALE");
      assertThat(listing.latestTrade().canceled()).isFalse();
    });
  }

  @Test
  void appliesViewportBoundsAndTransactionType() {
    var listings = controller.apartments(
        "11350", "JEONSE", 0L, Long.MAX_VALUE,
        BigDecimal.ZERO, new BigDecimal("300"),
        false, "127.05,37.65,127.07,37.68");

    assertThat(listings).extracting(listing -> listing.building().name())
        .containsExactlyInAnyOrder("상계주공12단지", "상계보람");
    assertThat(listings).allSatisfy(listing ->
        assertThat(listing.latestTrade().transactionType()).isEqualTo("JEONSE"));
  }

  @Test
  void rejectsUnsupportedTransactionType() {
    assertThatThrownBy(() -> controller.apartments(
        "11350", "UNKNOWN", 0L, Long.MAX_VALUE,
        BigDecimal.ZERO, new BigDecimal("300"), false, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void keepsApartmentsWithoutMatchingTradesWhenRequested() {
    var listings = controller.apartments(
        "11350", "SALE", 0L, Long.MAX_VALUE,
        new BigDecimal("200"), new BigDecimal("300"), true, null);

    assertThat(listings).hasSize(5);
    assertThat(listings).allSatisfy(listing -> {
      assertThat(listing.latestTrade()).isNull();
      assertThat(listing.sourceStatus()).isEqualTo("NO_MATCHING_TRADE");
    });
  }
}
