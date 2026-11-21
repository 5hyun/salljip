package com.housinglab.api.usage;

/** 사용량을 세는 외부 API. */
public enum ApiProvider {
  MOLIT("국토부 실거래가", "https://www.data.go.kr"),
  VWORLD("브이월드 지오코더", "https://www.vworld.kr"),
  SEOUL_OPEN_DATA("서울 열린데이터", "https://data.seoul.go.kr"),
  NAVER_MAP("네이버 지도", "https://console.ncloud.com");

  private final String label;
  private final String consoleUrl;

  ApiProvider(String label, String consoleUrl) {
    this.label = label;
    this.consoleUrl = consoleUrl;
  }

  public String label() { return label; }

  public String consoleUrl() { return consoleUrl; }
}
