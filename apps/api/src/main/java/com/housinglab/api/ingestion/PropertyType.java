package com.housinglab.api.ingestion;

/** 국토교통부 실거래가 매매 API 중 이 프로젝트가 수집하는 주택 유형. */
public enum PropertyType {
  APT("MOLIT_APT_TRADE", "RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev", "aptNm"),
  RH("MOLIT_RH_TRADE", "RTMSDataSvcRHTrade2/getRTMSDataSvcRHTrade2", "mhouseNm"),
  OFFI("MOLIT_OFFI_TRADE", "RTMSDataSvcOffiTrade/getRTMSDataSvcOffiTrade", "offiNm");

  private final String datasetId;
  private final String path;
  private final String nameField;

  PropertyType(String datasetId, String path, String nameField) {
    this.datasetId = datasetId;
    this.path = path;
    this.nameField = nameField;
  }

  public String datasetId() { return datasetId; }
  public String path() { return path; }
  public String nameField() { return nameField; }
}
