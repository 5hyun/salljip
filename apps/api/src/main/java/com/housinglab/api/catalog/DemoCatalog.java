package com.housinglab.api.catalog;

import static com.housinglab.api.catalog.CatalogModels.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DemoCatalog {
  private final List<Region> regions = List.of(
      new Region("11350", "서울특별시 노원구", "서울특별시"),
      new Region("11380", "서울특별시 은평구", "서울특별시"),
      new Region("11710", "서울특별시 송파구", "서울특별시"));

  private final List<Building> buildings = List.of(
      new Building("b-sanggye-12", "11350", "상계주공12단지", "서울 노원구 동일로 1538", 127.0607, 37.6673, 1739, 1988),
      new Building("b-junggye-5", "11350", "중계주공5단지", "서울 노원구 중계로 230", 127.0748, 37.6519, 2328, 1992),
      new Building("b-wolgye-skhub", "11350", "월계센트럴아이파크", "서울 노원구 초안산로2라길 26", 127.0571, 37.6325, 859, 2020),
      new Building("b-gongneung-punglim", "11350", "공릉풍림아이원", "서울 노원구 섬밭로 139", 127.0728, 37.6234, 1601, 2001),
      new Building("b-sanggye-boram", "11350", "상계보람", "서울 노원구 한글비석로 480", 127.0664, 37.6605, 3315, 1988),
      new Building("b-eunpyeong-lotte", "11380", "백련산롯데캐슬", "서울 은평구 백련산로 38", 126.9235, 37.5938, 1160, 2011));

  private final List<Trade> trades = List.of(
      trade("t1", "b-sanggye-12", "2021-09-18", "49.94", 590_000_000L, 8),
      trade("t2", "b-sanggye-12", "2021-11-06", "49.94", 620_000_000L, 12),
      trade("t3", "b-sanggye-12", "2022-01-17", "49.94", 605_000_000L, 4),
      trade("t4", "b-sanggye-12", "2026-08-11", "49.94", 710_000_000L, 9),
      trade("t5", "b-sanggye-12", "2026-10-03", "49.94", 735_000_000L, 11),
      trade("t6", "b-junggye-5", "2021-10-22", "58.46", 820_000_000L, 6),
      trade("t7", "b-junggye-5", "2021-12-14", "58.46", 845_000_000L, 10),
      trade("t8", "b-junggye-5", "2026-09-01", "58.46", 925_000_000L, 3),
      trade("t9", "b-wolgye-skhub", "2021-11-11", "59.99", 990_000_000L, 14),
      trade("t10", "b-wolgye-skhub", "2026-06-18", "59.99", 1_230_000_000L, 17),
      trade("t11", "b-gongneung-punglim", "2021-08-29", "59.94", 735_000_000L, 7),
      trade("t12", "b-gongneung-punglim", "2021-11-27", "59.94", 770_000_000L, 15),
      trade("t13", "b-gongneung-punglim", "2026-07-21", "59.94", 860_000_000L, 12),
      trade("t14", "b-sanggye-boram", "2021-10-03", "44.33", 515_000_000L, 5),
      trade("t15", "b-sanggye-boram", "2021-12-30", "44.33", 540_000_000L, 9),
      trade("t16", "b-sanggye-boram", "2026-09-22", "44.33", 645_000_000L, 13),
      rent("j1", "b-sanggye-12", "2026-09-14", "49.94", 320_000_000L, 0L, 7, "JEONSE"),
      rent("j1-old", "b-sanggye-12", "2025-08-18", "49.94", 295_000_000L, 0L, 10, "JEONSE"),
      rent("j2", "b-junggye-5", "2026-10-08", "58.46", 430_000_000L, 0L, 11, "JEONSE"),
      rent("j2-old", "b-junggye-5", "2025-09-02", "58.46", 405_000_000L, 0L, 6, "JEONSE"),
      rent("j3", "b-wolgye-skhub", "2026-08-21", "59.99", 610_000_000L, 0L, 15, "JEONSE"),
      rent("j3-old", "b-wolgye-skhub", "2025-07-11", "59.99", 575_000_000L, 0L, 9, "JEONSE"),
      rent("j4", "b-gongneung-punglim", "2026-10-27", "59.94", 445_000_000L, 0L, 9, "JEONSE"),
      rent("j4-old", "b-gongneung-punglim", "2025-10-16", "59.94", 420_000_000L, 0L, 12, "JEONSE"),
      rent("j5", "b-sanggye-boram", "2026-11-02", "44.33", 295_000_000L, 0L, 6, "JEONSE"),
      rent("j5-old", "b-sanggye-boram", "2025-11-04", "44.33", 275_000_000L, 0L, 3, "JEONSE"),
      rent("m1", "b-sanggye-12", "2026-10-10", "49.94", 50_000_000L, 950_000L, 5, "MONTHLY"),
      rent("m1-old", "b-sanggye-12", "2025-09-20", "49.94", 50_000_000L, 870_000L, 8, "MONTHLY"),
      rent("m2", "b-junggye-5", "2026-09-29", "58.46", 80_000_000L, 1_350_000L, 8, "MONTHLY"),
      rent("m2-old", "b-junggye-5", "2025-08-15", "58.46", 80_000_000L, 1_250_000L, 5, "MONTHLY"),
      rent("m3", "b-wolgye-skhub", "2026-10-17", "59.99", 100_000_000L, 1_750_000L, 12, "MONTHLY"),
      rent("m3-old", "b-wolgye-skhub", "2025-10-11", "59.99", 100_000_000L, 1_620_000L, 7, "MONTHLY"),
      rent("m4", "b-gongneung-punglim", "2026-11-04", "59.94", 60_000_000L, 1_200_000L, 10, "MONTHLY"),
      rent("m4-old", "b-gongneung-punglim", "2025-10-01", "59.94", 60_000_000L, 1_100_000L, 6, "MONTHLY"),
      rent("m5", "b-sanggye-boram", "2026-10-31", "44.33", 40_000_000L, 900_000L, 4, "MONTHLY"),
      rent("m5-old", "b-sanggye-boram", "2025-11-01", "44.33", 40_000_000L, 820_000L, 9, "MONTHLY"),
      new Trade("cancelled", "b-sanggye-boram", LocalDate.parse("2021-11-01"), new BigDecimal("44.33"), 480_000_000L, 0L, 2, "SALE", true, "MOLIT_DEMO"));

  private static Trade trade(String id, String building, String date, String area, long price, int floor) {
    return new Trade(id, building, LocalDate.parse(date), new BigDecimal(area), price, 0L, floor, "SALE", false, "MOLIT_DEMO");
  }

  private static Trade rent(String id, String building, String date, String area, long deposit, long monthlyRent, int floor, String type) {
    return new Trade(id, building, LocalDate.parse(date), new BigDecimal(area), deposit, monthlyRent, floor, type, false, "MOLIT_DEMO");
  }

  public List<Region> regions() { return regions; }
  public List<Building> buildings() { return buildings; }
  public List<Trade> trades() { return trades; }
}
