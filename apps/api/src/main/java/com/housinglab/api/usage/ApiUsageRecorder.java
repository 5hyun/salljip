package com.housinglab.api.usage;

import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 외부 API를 실제로 호출할 때마다(재시도 포함) 이 기기의 일별 사용량에 +1 한다. */
@Component
public class ApiUsageRecorder {
  static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final Logger log = LoggerFactory.getLogger(ApiUsageRecorder.class);

  private final JdbcTemplate jdbc;
  private final String source;

  @Autowired
  public ApiUsageRecorder(JdbcTemplate jdbc, @Value("${housing-lab.usage.source:local}") String source) {
    this.jdbc = jdbc;
    this.source = source.trim();
  }

  /** 테스트처럼 DB 없이 쓰는 경우. */
  public static ApiUsageRecorder disabled() { return new ApiUsageRecorder(null, "local"); }

  public String source() { return source; }

  public void success(ApiProvider provider) { record(provider, false); }

  public void failure(ApiProvider provider) { record(provider, true); }

  private void record(ApiProvider provider, boolean error) {
    if (jdbc == null) return;
    try {
      jdbc.update("""
          INSERT INTO api_usage_daily(source, provider, usage_date, calls, errors) VALUES (?, ?, ?, 1, ?)
          ON CONFLICT (source, provider, usage_date) DO UPDATE
             SET calls = api_usage_daily.calls + 1, errors = api_usage_daily.errors + EXCLUDED.errors, updated_at = now()""",
          source, provider.name(), LocalDate.now(SEOUL), error ? 1 : 0);
    } catch (RuntimeException e) {
      // 사용량 기록 실패가 본래 호출을 막으면 안 된다.
      log.warn("API 사용량 기록 실패({}): {}", provider, e.getMessage());
    }
  }
}
