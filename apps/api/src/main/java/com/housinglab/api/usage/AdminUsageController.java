package com.housinglab.api.usage;

import static com.housinglab.api.usage.UsageModels.*;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 관리자 사용량 조회·동기화. 모든 요청은 X-Admin-Token 헤더가 ADMIN_TOKEN 과 같아야 한다. */
@RestController
@RequestMapping("/api/admin/usage")
public class AdminUsageController {
  static final String TOKEN_HEADER = "X-Admin-Token";

  private final ApiUsageStore store;
  private final ApiUsageRecorder recorder;
  private final ApiUsageSyncer syncer;
  private final Environment env;
  private final byte[] adminToken;

  public AdminUsageController(
      ApiUsageStore store,
      ApiUsageRecorder recorder,
      Optional<ApiUsageSyncer> syncer,
      Environment env,
      @Value("${housing-lab.admin-token:}") String adminToken) {
    this.store = store;
    this.recorder = recorder;
    this.syncer = syncer.orElse(null);
    this.env = env;
    this.adminToken = adminToken.trim().getBytes(StandardCharsets.UTF_8);
  }

  @GetMapping
  UsageReport usage(
      @RequestHeader(value = TOKEN_HEADER, required = false) String token,
      @RequestParam(defaultValue = "30") int days) {
    authorize(token);
    if (days < 1 || days > 90) throw new IllegalArgumentException("days는 1~90 사이여야 합니다.");
    var providers = Arrays.stream(ApiProvider.values())
        .map(p -> new ProviderInfo(p.name(), p.label(), p.consoleUrl(),
            env.getProperty("housing-lab.usage.limit." + p.name().toLowerCase(Locale.ROOT), Integer.class, 0)))
        .toList();
    var sync = new SyncStatus(
        syncer != null,
        syncer == null ? null : syncer.target(),
        syncer == null ? null : syncer.schedule(),
        syncer == null ? null : store.lastAttempt().orElse(null),
        store.received());
    var from = LocalDate.now(ApiUsageRecorder.SEOUL).minusDays(days - 1);
    return new UsageReport(recorder.source(), providers, sync, store.since(from));
  }

  /** 보내는 쪽(Mac)이 최근 누적값을 보낸다. */
  @PostMapping("/import")
  ImportResult importUsage(
      @RequestHeader(value = TOKEN_HEADER, required = false) String token,
      @Valid @RequestBody ImportRequest request) {
    authorize(token);
    if (request.source().equals(recorder.source())) {
      throw new IllegalArgumentException("이 기기(" + recorder.source() + ") 자신의 기록은 덮어쓸 수 없습니다.");
    }
    var known = Arrays.stream(ApiProvider.values()).map(ApiProvider::name).toList();
    request.rows().stream().filter(row -> !known.contains(row.provider())).findFirst().ifPresent(row -> {
      throw new IllegalArgumentException("알 수 없는 provider: " + row.provider());
    });
    store.importRows(request.source(), request.rows());
    return new ImportResult(request.source(), request.rows().size());
  }

  /** 관리자 페이지의 [지금 동기화] 버튼. 보내는 쪽에서만 동작한다. */
  @PostMapping("/sync")
  SyncAttempt syncNow(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
    authorize(token);
    if (syncer == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이 기기는 동기화를 보내지 않습니다. Mac 관리자 페이지에서 동기화하세요.");
    }
    return syncer.sync(true);
  }

  private void authorize(String token) {
    if (adminToken.length == 0) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ADMIN_TOKEN이 설정되지 않아 관리자 기능을 쓸 수 없습니다.");
    }
    if (token == null || !MessageDigest.isEqual(adminToken, token.trim().getBytes(StandardCharsets.UTF_8))) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "관리자 토큰이 올바르지 않습니다.");
    }
  }
}
