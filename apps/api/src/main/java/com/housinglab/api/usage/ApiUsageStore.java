package com.housinglab.api.usage;

import static com.housinglab.api.usage.UsageModels.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ApiUsageStore {
  private final JdbcTemplate jdbc;

  public ApiUsageStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  /** from 이후 모든 기기의 일별 사용량. */
  public List<DailyUsage> since(LocalDate from) {
    return jdbc.query("""
        SELECT source, provider, usage_date, calls, errors FROM api_usage_daily
         WHERE usage_date >= ? ORDER BY usage_date DESC, provider, source""",
        (rs, i) -> new DailyUsage(rs.getString(1), rs.getString(2), rs.getDate(3).toLocalDate(), rs.getInt(4), rs.getInt(5)),
        from);
  }

  public List<DailyUsage> since(String source, LocalDate from) {
    return since(from).stream().filter(row -> row.source().equals(source)).toList();
  }

  /** 받은 누적값으로 덮어쓴다. 같은 요청을 여러 번 받아도 결과가 같다. */
  @Transactional
  public void importRows(String source, List<ImportRow> rows) {
    for (var row : rows) {
      var provider = ApiProvider.valueOf(row.provider());
      jdbc.update("""
          INSERT INTO api_usage_daily(source, provider, usage_date, calls, errors) VALUES (?, ?, ?, ?, ?)
          ON CONFLICT (source, provider, usage_date) DO UPDATE
             SET calls = EXCLUDED.calls, errors = EXCLUDED.errors, updated_at = now()""",
          source, provider.name(), row.date(), row.calls(), row.errors());
    }
    jdbc.update("""
        INSERT INTO usage_sync_received(source, received_at, row_count) VALUES (?, now(), ?)
        ON CONFLICT (source) DO UPDATE SET received_at = now(), row_count = EXCLUDED.row_count""",
        source, rows.size());
  }

  public List<SyncReceived> received() {
    return jdbc.query("SELECT source, received_at, row_count FROM usage_sync_received ORDER BY source",
        (rs, i) -> new SyncReceived(rs.getString(1), rs.getTimestamp(2).toInstant(), rs.getInt(3)));
  }

  public void logAttempt(SyncAttempt attempt) {
    jdbc.update("INSERT INTO usage_sync_log(attempted_at, manual, success, message) VALUES (?, ?, ?, ?)",
        Timestamp.from(attempt.attemptedAt()), attempt.manual(), attempt.success(), attempt.message());
  }

  public Optional<SyncAttempt> lastAttempt() {
    return jdbc.query("SELECT attempted_at, manual, success, message FROM usage_sync_log ORDER BY attempted_at DESC LIMIT 1",
        (rs, i) -> new SyncAttempt(rs.getTimestamp(1).toInstant(), rs.getBoolean(2), rs.getBoolean(3), rs.getString(4)))
        .stream().findFirst();
  }
}
