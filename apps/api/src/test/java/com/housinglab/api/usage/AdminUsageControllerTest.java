package com.housinglab.api.usage;

import static com.housinglab.api.usage.UsageModels.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

class AdminUsageControllerTest {
  private final FakeStore store = new FakeStore();
  private final ApiUsageRecorder recorder = new ApiUsageRecorder(null, "minipc");
  private final MockEnvironment env = new MockEnvironment().withProperty("housing-lab.usage.limit.vworld", "40000");

  private AdminUsageController controller(String adminToken) {
    return new AdminUsageController(store, recorder, Optional.empty(), env, adminToken);
  }

  @Test
  void refusesWhenAdminTokenIsNotConfigured() {
    assertThatThrownBy(() -> controller("").usage("anything", 30))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("503");
  }

  @Test
  void refusesWrongToken() {
    assertThatThrownBy(() -> controller("secret").usage("wrong", 30))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("401");
    assertThatThrownBy(() -> controller("secret").usage(null, 30))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  void reportsProvidersWithConfiguredLimits() {
    var report = controller("secret").usage("secret", 30);

    assertThat(report.source()).isEqualTo("minipc");
    assertThat(report.sync().sender()).isFalse();
    assertThat(report.providers()).extracting(ProviderInfo::id)
        .containsExactly("MOLIT", "VWORLD", "SEOUL_OPEN_DATA", "NAVER_MAP");
    assertThat(report.providers()).filteredOn(p -> p.id().equals("VWORLD"))
        .extracting(ProviderInfo::dailyLimit).containsExactly(40000);
  }

  @Test
  void importsOtherSourceButNotItsOwn() {
    var rows = List.of(new ImportRow("VWORLD", LocalDate.of(2026, 11, 21), 3120, 2));

    var result = controller("secret").importUsage("secret", new ImportRequest("mac", rows));
    assertThat(result.rowCount()).isEqualTo(1);
    assertThat(store.imported).containsExactly("mac");

    assertThatThrownBy(() -> controller("secret").importUsage("secret", new ImportRequest("minipc", rows)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnknownProvider() {
    var rows = List.of(new ImportRow("UNKNOWN", LocalDate.of(2026, 11, 21), 1, 0));

    assertThatThrownBy(() -> controller("secret").importUsage("secret", new ImportRequest("mac", rows)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(store.imported).isEmpty();
  }

  @Test
  void manualSyncIsOnlyForSender() {
    assertThatThrownBy(() -> controller("secret").syncNow("secret"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("409");
  }

  @Test
  void failedSyncIsLoggedWithoutThrowing() {
    var syncer = new ApiUsageSyncer(store, new ApiUsageRecorder(null, "mac"), "http://127.0.0.1:1", "", "secret", "0 50 23 * * *");

    var attempt = syncer.sync(true);

    assertThat(attempt.success()).isFalse();
    assertThat(attempt.manual()).isTrue();
    assertThat(store.attempts).containsExactly(attempt);
  }

  /** DB 없이 컨트롤러·동기화 흐름만 확인한다. SQL은 PostgreSQL에서 직접 확인. */
  static class FakeStore extends ApiUsageStore {
    final List<String> imported = new ArrayList<>();
    final List<SyncAttempt> attempts = new ArrayList<>();

    FakeStore() { super(null); }

    @Override public List<DailyUsage> since(LocalDate from) { return List.of(); }

    @Override public void importRows(String source, List<ImportRow> rows) { imported.add(source); }

    @Override public List<SyncReceived> received() { return List.of(); }

    @Override public void logAttempt(SyncAttempt attempt) { attempts.add(attempt); }

    @Override public Optional<SyncAttempt> lastAttempt() { return attempts.stream().reduce((a, b) -> b); }
  }
}
