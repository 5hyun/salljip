package com.housinglab.api.usage;

import static com.housinglab.api.usage.UsageModels.*;

import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 보내는 쪽(Mac)에서만 켜진다. 하루 1번 최근 30일 누적값을 미니PC로 보내고, 실패하면 재시도하지 않는다.
 * 누적값을 덮어쓰는 방식이라 며칠 못 보내도 다음 성공 때 맞춰진다.
 */
@Component
@EnableScheduling
@ConditionalOnExpression("!'${housing-lab.usage.sync-url:}'.isBlank()")
public class ApiUsageSyncer {
  static final int DAYS = 30;
  private static final Logger log = LoggerFactory.getLogger(ApiUsageSyncer.class);

  private final ApiUsageStore store;
  private final String source;
  private final String target;
  private final String schedule;
  private final RestClient restClient;

  public ApiUsageSyncer(
      ApiUsageStore store,
      ApiUsageRecorder recorder,
      @Value("${housing-lab.usage.sync-url}") String target,
      @Value("${housing-lab.usage.sync-token:}") String token,
      @Value("${housing-lab.admin-token:}") String adminToken,
      @Value("${housing-lab.usage.sync-cron}") String schedule) {
    this.store = store;
    this.source = recorder.source();
    this.target = target.trim();
    this.schedule = schedule;
    var requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(5_000);
    requestFactory.setReadTimeout(15_000);
    this.restClient = RestClient.builder()
        .baseUrl(this.target)
        .requestFactory(requestFactory)
        // 받는 쪽 토큰이 이 기기의 ADMIN_TOKEN과 다를 때만 USAGE_SYNC_TOKEN을 따로 둔다.
        .defaultHeader(AdminUsageController.TOKEN_HEADER, (token.isBlank() ? adminToken : token).trim())
        .build();
  }

  public String target() { return target; }

  public String schedule() { return schedule; }

  @Scheduled(cron = "${housing-lab.usage.sync-cron}", zone = "Asia/Seoul")
  void daily() { sync(false); }

  public synchronized SyncAttempt sync(boolean manual) {
    var rows = store.since(source, LocalDate.now(ApiUsageRecorder.SEOUL).minusDays(DAYS - 1)).stream()
        .map(row -> new ImportRow(row.provider(), row.date(), row.calls(), row.errors()))
        .toList();
    SyncAttempt attempt;
    try {
      restClient.post().uri("/api/admin/usage/import")
          .body(new ImportRequest(source, rows))
          .retrieve()
          .toBodilessEntity();
      attempt = new SyncAttempt(Instant.now(), manual, true, rows.size() + "행 전송");
    } catch (RestClientException e) {
      log.warn("사용량 동기화 실패({}): {}", target, e.getMessage());
      attempt = new SyncAttempt(Instant.now(), manual, false, truncate(e.getMessage()));
    }
    store.logAttempt(attempt);
    return attempt;
  }

  private static String truncate(String message) {
    if (message == null) return "알 수 없는 오류";
    return message.length() > 500 ? message.substring(0, 500) : message;
  }
}
