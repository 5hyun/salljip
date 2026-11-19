package com.housinglab.api.ingestion;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** 실거래가 API 항목 하나를 정규화한 값. */
public record TradeRecord(
    PropertyType type,
    String houseType,
    String regionCode,
    String umdName,
    String jibun,
    String name,
    String roadAddress,
    BigDecimal areaM2,
    long priceKrw,
    LocalDate contractDate,
    Integer floor,
    Integer builtYear,
    LocalDate canceledOn,
    boolean canceled) {

  private static final DateTimeFormatter CANCEL_DAY = DateTimeFormatter.ofPattern("yy.MM.dd");

  public static TradeRecord from(PropertyType type, Map<String, String> item) {
    String umd = item.getOrDefault("umdNm", "");
    String jibun = item.getOrDefault("jibun", "");
    String name = item.getOrDefault(type.nameField(), "");
    if (name.isBlank()) name = (umd + " " + jibun).trim();
    boolean canceled = "O".equalsIgnoreCase(item.getOrDefault("cdealType", ""));
    return new TradeRecord(
        type,
        blankToNull(item.get("houseType")),
        item.getOrDefault("sggCd", ""),
        umd,
        blankToNull(jibun),
        name,
        roadAddress(item),
        new BigDecimal(item.get("excluUseAr")),
        Long.parseLong(item.get("dealAmount").replace(",", "").trim()) * 10_000L,
        LocalDate.of(
            Integer.parseInt(item.get("dealYear")), Integer.parseInt(item.get("dealMonth")), Integer.parseInt(item.get("dealDay"))),
        parseInt(item.get("floor")),
        parseInt(item.get("buildYear")),
        canceled ? parseCancelDay(item.get("cdealDay")) : null,
        canceled);
  }

  /** 같은 건물로 묶는 키. 아파트는 국토부 단지 일련번호, 나머지는 유형+법정동+지번. */
  public String buildingKey(String aptSeq) {
    if (type == PropertyType.APT && aptSeq != null && !aptSeq.isBlank()) return "APT:" + aptSeq;
    if (jibun == null) return type + ":" + regionCode + ":" + umdName + ":?:" + name;
    return type + ":" + regionCode + ":" + umdName + ":" + jibun;
  }

  /**
   * 해제 여부와 무관한 거래 식별 지문. 같은 달에 조건이 완전히 같은 거래가 여러 건이면
   * {@code occurrence}로 구분한다.
   */
  public String fingerprint(String buildingKey, int occurrence) {
    String base = String.join("|", buildingKey, areaM2.stripTrailingZeros().toPlainString(), String.valueOf(priceKrw),
        contractDate.toString(), String.valueOf(floor), String.valueOf(occurrence));
    return type.datasetId() + ":" + sha256(base).substring(0, 40);
  }

  public static String contentHash(Map<String, String> item) {
    return sha256(new TreeMap<>(item).toString());
  }

  private static String roadAddress(Map<String, String> item) {
    String road = item.getOrDefault("roadNm", "");
    if (road.isBlank()) return null;
    int main = parseInt(item.get("roadNmBonbun")) == null ? 0 : parseInt(item.get("roadNmBonbun"));
    Integer sub = parseInt(item.get("roadNmBubun"));
    if (main == 0) return road;
    return road + " " + main + (sub == null || sub == 0 ? "" : "-" + sub);
  }

  private static LocalDate parseCancelDay(String value) {
    try {
      return value == null || value.isBlank() ? null : LocalDate.parse(value.trim(), CANCEL_DAY);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static Integer parseInt(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
