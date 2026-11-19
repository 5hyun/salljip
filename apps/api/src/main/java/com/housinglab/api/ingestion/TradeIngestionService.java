package com.housinglab.api.ingestion;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 구·월 단위로 실거래가를 받아 raw_trade_records → buildings/trades 로 적재한다.
 * 같은 달을 다시 받아도 중복되지 않고, 해제 여부가 바뀐 거래만 새 버전으로 기록한다.
 */
@Service
@ConditionalOnProperty(name = "housing-lab.demo-mode", havingValue = "false")
public class TradeIngestionService {
  private static final Logger log = LoggerFactory.getLogger(TradeIngestionService.class);
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final String KEY_PROVIDER = "MOLIT";

  private final MolitTradeClient client;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final Map<String, UUID> buildingIds = new HashMap<>();

  public TradeIngestionService(MolitTradeClient client, JdbcTemplate jdbc, TransactionTemplate tx) {
    this.client = client;
    this.jdbc = jdbc;
    this.tx = tx;
  }

  public record Summary(PropertyType type, int months, int skippedMonths, int fetched, int inserted, int updated, String error) {}

  /**
   * @param refreshRecentMonths 최근 N개월은 이미 받았어도 다시 받는다(신고 기한 30일, 해제 신고 반영).
   */
  public List<Summary> ingest(String regionCode, YearMonth from, YearMonth to, List<PropertyType> types, int refreshRecentMonths) {
    if (!client.configured()) throw new IllegalStateException("MOLIT_API_KEY 가 설정되지 않았습니다.");
    Integer regionCount = jdbc.queryForObject("SELECT COUNT(*) FROM regions WHERE region_code = ?", Integer.class, regionCode);
    if (regionCount == null || regionCount == 0) throw new IllegalArgumentException("알 수 없는 지역코드: " + regionCode);

    YearMonth refreshFrom = YearMonth.now(SEOUL).minusMonths(refreshRecentMonths);
    var summaries = new ArrayList<Summary>();
    for (var type : types) {
      int months = 0, skipped = 0, fetched = 0, inserted = 0, updated = 0;
      String error = null;
      for (var month = from; !month.isAfter(to); month = month.plusMonths(1)) {
        if (month.isBefore(refreshFrom) && alreadyIngested(type, regionCode, month)) { skipped++; continue; }
        try {
          var result = ingestMonth(type, regionCode, month);
          months++;
          fetched += result[0];
          inserted += result[1];
          updated += result[2];
          log.info("{} {} {}: 받음 {} / 신규 {} / 갱신 {}", type, regionCode, month, result[0], result[1], result[2]);
        } catch (MolitApiException e) {
          error = e.getMessage();
          log.warn("{} {} {} 수집 실패: {}", type, regionCode, month, e.getMessage());
          // 미등록 키·트래픽 초과는 남은 달도 모두 실패하므로 이 유형은 중단한다.
          if (e.unregisteredKey() || e.quotaExceeded()) break;
        }
      }
      summaries.add(new Summary(type, months, skipped, fetched, inserted, updated, error));
    }
    return summaries;
  }

  private boolean alreadyIngested(PropertyType type, String regionCode, YearMonth month) {
    Integer count = jdbc.queryForObject("""
        SELECT COUNT(*) FROM ingestion_runs
         WHERE dataset_id = ? AND region_code = ? AND period = ? AND status = 'SUCCEEDED'""",
        Integer.class, type.datasetId(), regionCode, month.toString());
    return count != null && count > 0;
  }

  /** @return [받은 건수, 신규 거래, 해제 여부 갱신] */
  private int[] ingestMonth(PropertyType type, String regionCode, YearMonth month) {
    var items = new ArrayList<Map<String, String>>();
    for (int page = 1; ; page++) {
      var result = client.fetch(type, regionCode, month, page);
      items.addAll(result.items());
      if (result.items().isEmpty() || items.size() >= result.totalCount()) break;
    }

    UUID runId = UUID.randomUUID();
    Integer generation = jdbc.queryForObject("""
        SELECT COALESCE(MAX(run_generation), 0) + 1 FROM ingestion_runs
         WHERE dataset_id = ? AND region_code = ? AND period = ?""",
        Integer.class, type.datasetId(), regionCode, month.toString());
    jdbc.update("""
        INSERT INTO ingestion_runs(id, dataset_id, region_code, period, status, started_at, run_generation)
        VALUES (?, ?, ?, ?, 'RUNNING', now(), ?)""", runId, type.datasetId(), regionCode, month.toString(), generation);

    try {
      int[] counts = tx.execute(status -> store(type, regionCode, month, items, runId));
      jdbc.update("UPDATE ingestion_runs SET status = 'SUCCEEDED', finished_at = now(), cursor_value = ? WHERE id = ?",
          String.valueOf(items.size()), runId);
      return new int[] {items.size(), counts[0], counts[1]};
    } catch (RuntimeException e) {
      buildingIds.clear(); // 롤백된 건물 ID가 캐시에 남지 않게 한다.
      jdbc.update("UPDATE ingestion_runs SET status = 'FAILED', finished_at = now() WHERE id = ?", runId);
      throw e;
    }
  }

  private int[] store(PropertyType type, String regionCode, YearMonth month, List<Map<String, String>> items, UUID batchId) {
    var occurrences = new HashMap<String, Integer>();
    Timestamp now = Timestamp.from(Instant.now());
    int inserted = 0, updated = 0;
    for (var item : items) {
      var record = TradeRecord.from(type, item);
      String buildingKey = record.buildingKey(item.get("aptSeq"));
      String baseFingerprint = record.fingerprint(buildingKey, 0);
      int occurrence = occurrences.merge(baseFingerprint, 1, Integer::sum) - 1;
      String fingerprint = record.fingerprint(buildingKey, occurrence);

      UUID rawId = storeRaw(type, regionCode, month, item, batchId, now);
      UUID buildingId = building(record, buildingKey);
      Timestamp canceledAt = !record.canceled() ? null
          : record.canceledOn() == null ? now : Timestamp.from(record.canceledOn().atStartOfDay(SEOUL).toInstant());

      var existing = jdbc.query(
          "SELECT id, canceled_at IS NOT NULL AS canceled, source_version FROM trades WHERE source_fingerprint = ?",
          (rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getBoolean(2), rs.getInt(3)}, fingerprint);
      if (existing.isEmpty()) {
        UUID tradeId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO trades(id, source_fingerprint, building_id, region_code, contract_date, floor,
                               exclusive_area_m2, price_krw, trade_type, canceled_at, source_version, data_as_of)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'SALE', ?, 1, ?)""",
            tradeId, fingerprint, buildingId, regionCode, record.contractDate(), record.floor(),
            record.areaM2(), record.priceKrw(), canceledAt, now);
        jdbc.update("INSERT INTO trade_versions(trade_id, version, observed_at, operation, raw_id) VALUES (?, 1, ?, 'INSERT', ?)",
            tradeId, now, rawId);
        inserted++;
      } else if ((boolean) existing.getFirst()[1] != record.canceled()) {
        UUID tradeId = (UUID) existing.getFirst()[0];
        int version = (int) existing.getFirst()[2] + 1;
        jdbc.update("UPDATE trades SET canceled_at = ?, source_version = ?, data_as_of = ? WHERE id = ?",
            canceledAt, version, now, tradeId);
        jdbc.update("INSERT INTO trade_versions(trade_id, version, observed_at, operation, raw_id) VALUES (?, ?, ?, ?, ?)",
            tradeId, version, now, record.canceled() ? "CANCEL" : "RESTORE", rawId);
        updated++;
      }
    }
    return new int[] {inserted, updated};
  }

  private UUID storeRaw(PropertyType type, String regionCode, YearMonth month, Map<String, String> item, UUID batchId, Timestamp now) {
    String hash = TradeRecord.contentHash(item);
    String contractMonth = month.toString().replace("-", "");
    jdbc.update("""
        INSERT INTO raw_trade_records(id, provider, region_code, contract_month, payload_json, content_hash, fetched_at, batch_id)
        VALUES (?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?)
        ON CONFLICT (provider, region_code, contract_month, content_hash) DO NOTHING""",
        UUID.randomUUID(), type.datasetId(), regionCode, contractMonth, toJson(item), hash, now, batchId);
    return jdbc.queryForObject("""
        SELECT id FROM raw_trade_records WHERE provider = ? AND region_code = ? AND contract_month = ? AND content_hash = ?""",
        UUID.class, type.datasetId(), regionCode, contractMonth, hash);
  }

  private UUID building(TradeRecord record, String key) {
    UUID cached = buildingIds.get(key);
    if (cached != null) return cached;
    var found = jdbc.queryForList(
        "SELECT building_id FROM building_source_keys WHERE provider = ? AND provider_key = ?", UUID.class, KEY_PROVIDER, key);
    if (!found.isEmpty()) {
      buildingIds.put(key, found.getFirst());
      return found.getFirst();
    }
    UUID id = UUID.randomUUID();
    jdbc.update("""
        INSERT INTO buildings(id, region_code, canonical_name, addr_main, road_address, location, approved_at,
                              property_type, umd_name, jibun, built_year, geocode_status)
        VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, 'PENDING')""",
        id, record.regionCode(), record.name(), truncate(record.jibun(), 30), truncate(record.roadAddress(), 100),
        record.builtYear() == null ? null : LocalDate.of(record.builtYear(), 1, 1),
        propertyType(record), record.umdName(), record.jibun(), record.builtYear());
    jdbc.update("""
        INSERT INTO building_source_keys(id, building_id, provider, provider_key, confidence, verified_at)
        VALUES (?, ?, ?, ?, 1.0, now())""", UUID.randomUUID(), id, KEY_PROVIDER, key);
    buildingIds.put(key, id);
    return id;
  }

  /** 연립다세대 API는 houseType으로 연립/다세대를 구분해 준다. */
  static String propertyType(TradeRecord record) {
    if (record.type() != PropertyType.RH) return record.type().name();
    if ("연립".equals(record.houseType())) return "ROW";
    if ("다세대".equals(record.houseType())) return "MULTI";
    return "RH";
  }

  private static String truncate(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }

  private static String toJson(Map<String, String> item) {
    var sb = new StringBuilder("{");
    item.forEach((k, v) -> {
      if (sb.length() > 1) sb.append(',');
      sb.append(quote(k)).append(':').append(quote(v));
    });
    return sb.append('}').toString();
  }

  private static String quote(String value) {
    var sb = new StringBuilder("\"");
    for (char c : value.toCharArray()) {
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
        }
      }
    }
    return sb.append('"').toString();
  }
}
