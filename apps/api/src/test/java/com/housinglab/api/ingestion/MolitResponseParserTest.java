package com.housinglab.api.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MolitResponseParserTest {
  private static final String APT = """
      <?xml version="1.0" encoding="utf-8" standalone="yes"?><response><header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>
      <body><items><item><aptDong> </aptDong><aptNm>주공2</aptNm><aptSeq>11350-149</aptSeq><buildYear>1992</buildYear>
      <cdealDay> </cdealDay><cdealType> </cdealType><dealAmount>35,150</dealAmount><dealDay>21</dealDay><dealMonth>10</dealMonth>
      <dealYear>2026</dealYear><excluUseAr>44.94</excluUseAr><floor>13</floor><jibun>556</jibun><roadNm>초안산로1길</roadNm>
      <roadNmBonbun>00018</roadNmBonbun><roadNmBubun>00000</roadNmBubun><sggCd>11350</sggCd><umdNm>월계동</umdNm></item>
      <item><aptNm>주공2</aptNm><aptSeq>11350-149</aptSeq><cdealDay>26.11.02</cdealDay><cdealType>O</cdealType>
      <dealAmount>36,000</dealAmount><dealDay>3</dealDay><dealMonth>10</dealMonth><dealYear>2026</dealYear>
      <excluUseAr>44.94</excluUseAr><floor>-1</floor><jibun>556</jibun><sggCd>11350</sggCd><umdNm>월계동</umdNm></item></items>
      <numOfRows>10</numOfRows><pageNo>1</pageNo><totalCount>473</totalCount></body></response>""";

  private static final String RH = """
      <response><header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header><body><items><item>
      <buildYear>2010</buildYear><cdealType> </cdealType><dealAmount>20,500</dealAmount><dealDay>31</dealDay><dealMonth>10</dealMonth>
      <dealYear>2026</dealYear><excluUseAr>49.85</excluUseAr><floor>2</floor><houseType>다세대</houseType><jibun>75-45</jibun>
      <mhouseNm>민하우스</mhouseNm><sggCd>11350</sggCd><umdNm>상계동</umdNm></item></items><totalCount>1</totalCount></body></response>""";

  @Test
  void parsesApartmentItems() {
    var page = MolitResponseParser.parse(APT);
    assertThat(page.totalCount()).isEqualTo(473);
    assertThat(page.items()).hasSize(2);

    var item = page.items().getFirst();
    var record = TradeRecord.from(PropertyType.APT, item);
    assertThat(record.name()).isEqualTo("주공2");
    assertThat(record.priceKrw()).isEqualTo(351_500_000L);
    assertThat(record.areaM2()).isEqualByComparingTo(new BigDecimal("44.94"));
    assertThat(record.contractDate()).isEqualTo(LocalDate.of(2026, 10, 21));
    assertThat(record.floor()).isEqualTo(13);
    assertThat(record.builtYear()).isEqualTo(1992);
    assertThat(record.roadAddress()).isEqualTo("초안산로1길 18");
    assertThat(record.canceled()).isFalse();
    assertThat(record.buildingKey(item.get("aptSeq"))).isEqualTo("APT:11350-149");
  }

  @Test
  void readsCancellationAndBasementFloor() {
    var item = MolitResponseParser.parse(APT).items().get(1);
    var record = TradeRecord.from(PropertyType.APT, item);
    assertThat(record.canceled()).isTrue();
    assertThat(record.canceledOn()).isEqualTo(LocalDate.of(2026, 11, 2));
    assertThat(record.floor()).isEqualTo(-1);
  }

  @Test
  void groupsVillasByLotNumberAndKeepsHouseType() {
    var record = TradeRecord.from(PropertyType.RH, MolitResponseParser.parse(RH).items().getFirst());
    assertThat(record.name()).isEqualTo("민하우스");
    assertThat(record.buildingKey(null)).isEqualTo("RH:11350:상계동:75-45");
    assertThat(TradeIngestionService.propertyType(record)).isEqualTo("MULTI");
  }

  @Test
  void fingerprintIgnoresCancellationButSeparatesDuplicates() {
    var item = MolitResponseParser.parse(APT).items().getFirst();
    var record = TradeRecord.from(PropertyType.APT, item);
    var canceledItem = new java.util.HashMap<>(item);
    canceledItem.put("cdealType", "O");
    var canceled = TradeRecord.from(PropertyType.APT, canceledItem);

    assertThat(canceled.fingerprint("k", 0)).isEqualTo(record.fingerprint("k", 0));
    assertThat(record.fingerprint("k", 1)).isNotEqualTo(record.fingerprint("k", 0));
    assertThat(record.fingerprint("k", 0)).startsWith("MOLIT_APT_TRADE:");
  }

  @Test
  void surfacesGatewayErrors() {
    var xml = """
        <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</errMsg>
        <returnAuthMsg>등록되지 않은 서비스키</returnAuthMsg><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>""";
    assertThatThrownBy(() -> MolitResponseParser.parse(xml))
        .isInstanceOfSatisfying(MolitApiException.class, e -> assertThat(e.unregisteredKey()).isTrue());
  }
}
