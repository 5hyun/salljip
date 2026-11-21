package com.housinglab.api.usage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class UsageModels {
  private UsageModels() {}

  public record DailyUsage(String source, String provider, LocalDate date, int calls, int errors) {}

  public record SyncAttempt(Instant attemptedAt, boolean manual, boolean success, String message) {}

  public record SyncReceived(String source, Instant receivedAt, int rowCount) {}

  public record ProviderInfo(String id, String label, String consoleUrl, int dailyLimit) {}

  /**
   * @param sender 이 기기가 동기화를 보내는 쪽(Mac)인지
   * @param lastAttempt 보내는 쪽의 마지막 시도
   * @param received 받는 쪽(미니PC)이 기기별로 마지막으로 받은 기록
   */
  public record SyncStatus(boolean sender, String target, String schedule, SyncAttempt lastAttempt, List<SyncReceived> received) {}

  public record UsageReport(String source, List<ProviderInfo> providers, SyncStatus sync, List<DailyUsage> days) {}

  /** 보내는 쪽의 최근 누적값. 받는 쪽은 (source, provider, date) 기준으로 그대로 덮어쓴다. */
  public record ImportRequest(
      @NotBlank @Size(max = 30) String source,
      @NotNull @Size(max = 1000) List<@Valid ImportRow> rows) {}

  public record ImportRow(@NotBlank String provider, @NotNull LocalDate date, @Min(0) int calls, @Min(0) int errors) {}

  public record ImportResult(String source, int rowCount) {}
}
