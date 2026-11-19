package com.housinglab.api.ingestion;

public class MolitApiException extends RuntimeException {
  private final String code;

  public MolitApiException(String code, String message) {
    super("[" + code + "] " + message);
    this.code = code;
  }

  public String code() { return code; }

  /** 미등록 서비스키(30) 또는 활용신청하지 않은 API. 재시도해도 소용없다. */
  public boolean unregisteredKey() { return "30".equals(code); }

  /** 일일 트래픽 초과(22). */
  public boolean quotaExceeded() { return "22".equals(code); }
}
