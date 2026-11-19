package com.housinglab.api.catalog;

import static com.housinglab.api.catalog.CatalogModels.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 실거래가 수집기(ingestion 패키지)가 PostGIS에 적재한 국토교통부 매매 실거래를 읽는다.
 * 지도에 올릴 수 있도록 좌표가 확보된 건물만 반환한다. 전월세는 아직 수집하지 않는다.
 */
@Component
@ConditionalOnProperty(name = "housing-lab.demo-mode", havingValue = "false")
public class JdbcCatalog {
  private static final String BUILDING_SQL = """
      SELECT b.id, b.region_code, b.property_type, b.canonical_name,
             r.name || ' ' || COALESCE(b.road_address, CONCAT_WS(' ', b.umd_name, b.jibun)) AS address,
             ST_X(b.location) AS lon, ST_Y(b.location) AS lat, b.built_year
        FROM buildings b JOIN regions r ON r.region_code = b.region_code
       WHERE b.location IS NOT NULL""";
  private static final String TRADE_COLUMNS =
      "t.id, t.building_id, t.contract_date, t.exclusive_area_m2, t.price_krw, t.floor, t.canceled_at, t.source_fingerprint";

  private final JdbcTemplate jdbc;

  public JdbcCatalog(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  /** 건물별 최신·직전 매매와 조건에 맞는 거래 수. */
  public record BuildingSales(Trade latest, Trade previous, int count) {}

  public List<Building> buildings(Collection<String> regionCodes) {
    if (regionCodes == null || regionCodes.isEmpty()) return jdbc.query(BUILDING_SQL, JdbcCatalog::building);
    return jdbc.query(BUILDING_SQL + " AND b.region_code = ANY (?)", JdbcCatalog::building, (Object) textArray(regionCodes));
  }

  /** {@code from} 이후 계약된, 해제되지 않은 유사면적 매매. */
  public List<Trade> sales(Collection<String> regionCodes, LocalDate from, BigDecimal areaMin, BigDecimal areaMax) {
    return jdbc.query("SELECT " + TRADE_COLUMNS + """
         FROM trades t
        WHERE t.region_code = ANY (?) AND t.contract_date >= ? AND t.canceled_at IS NULL
          AND t.exclusive_area_m2 BETWEEN ? AND ? AND t.building_id IS NOT NULL""",
        JdbcCatalog::trade, textArray(regionCodes), from, areaMin, areaMax);
  }

  public Map<String, BuildingSales> latestSales(
      String regionCode, BigDecimal areaMin, BigDecimal areaMax, long priceMin, long priceMax) {
    var rows = jdbc.query("""
        SELECT * FROM (
          SELECT %s,
                 ROW_NUMBER() OVER (PARTITION BY t.building_id ORDER BY t.contract_date DESC, t.id) AS rn,
                 COUNT(*) OVER (PARTITION BY t.building_id) AS cnt
            FROM trades t
           WHERE t.region_code = ? AND t.canceled_at IS NULL AND t.building_id IS NOT NULL
             AND t.exclusive_area_m2 BETWEEN ? AND ? AND t.price_krw BETWEEN ? AND ?
        ) ranked WHERE rn <= 2 ORDER BY building_id, rn""".formatted(TRADE_COLUMNS),
        (rs, i) -> new Object[] {trade(rs, i), rs.getInt("cnt")},
        regionCode, areaMin, areaMax, priceMin, priceMax);
    var grouped = new HashMap<String, List<Object[]>>();
    rows.forEach(row -> grouped.computeIfAbsent(((Trade) row[0]).buildingId(), key -> new ArrayList<>()).add(row));
    var result = new HashMap<String, BuildingSales>();
    grouped.forEach((buildingId, list) -> result.put(buildingId, new BuildingSales(
        (Trade) list.get(0)[0], list.size() > 1 ? (Trade) list.get(1)[0] : null, (int) list.get(0)[1])));
    return result;
  }

  public List<Trade> tradesForBuilding(String buildingId, LocalDate from, LocalDate to, BigDecimal areaMin) {
    return jdbc.query("SELECT " + TRADE_COLUMNS + """
         FROM trades t
        WHERE t.building_id = ? AND t.canceled_at IS NULL
          AND (CAST(? AS DATE) IS NULL OR t.contract_date >= ?)
          AND (CAST(? AS DATE) IS NULL OR t.contract_date <= ?)
          AND (CAST(? AS NUMERIC) IS NULL OR t.exclusive_area_m2 >= ?)
        ORDER BY t.contract_date DESC""",
        JdbcCatalog::trade, UUID.fromString(buildingId), from, from, to, to, areaMin, areaMin);
  }

  public Instant dataAsOf() {
    Timestamp asOf = jdbc.queryForObject("SELECT MAX(data_as_of) FROM trades", Timestamp.class);
    return asOf == null ? Instant.EPOCH : asOf.toInstant();
  }

  public List<Map<String, String>> sources() {
    return jdbc.query("""
        SELECT source_id, dataset, permission, attribution FROM source_licenses
         WHERE source_id LIKE 'MOLIT\\_%' ORDER BY source_id""",
        (rs, i) -> Map.of("id", rs.getString(1), "name", rs.getString(2), "status", rs.getString(3), "attribution", rs.getString(4)));
  }

  public static boolean isDatabaseId(String buildingId) {
    try {
      UUID.fromString(buildingId);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /** pgjdbc는 String[]을 text[]로 바인딩한다. */
  private static String[] textArray(Collection<String> values) { return values.toArray(String[]::new); }

  private static Building building(ResultSet rs, int row) throws SQLException {
    Integer builtYear = rs.getObject("built_year", Integer.class);
    return new Building(
        rs.getString("id"), rs.getString("region_code"), rs.getString("canonical_name"), rs.getString("address"),
        rs.getDouble("lon"), rs.getDouble("lat"), 0, builtYear == null ? 0 : builtYear, rs.getString("property_type"));
  }

  private static Trade trade(ResultSet rs, int row) throws SQLException {
    String fingerprint = rs.getString("source_fingerprint");
    int colon = fingerprint.indexOf(':');
    return new Trade(
        rs.getString("id"), rs.getString("building_id"), rs.getObject("contract_date", LocalDate.class),
        rs.getBigDecimal("exclusive_area_m2"), rs.getLong("price_krw"), 0L, rs.getInt("floor"), "SALE",
        rs.getTimestamp("canceled_at") != null, colon < 0 ? "MOLIT" : fingerprint.substring(0, colon));
  }
}
