package com.housinglab.api.ingestion;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 공공데이터포털 국토교통부 실거래가 매매 API 호출. */
@Component
public class MolitTradeClient {
  private static final DateTimeFormatter DEAL_YMD = DateTimeFormatter.ofPattern("yyyyMM");
  static final int PAGE_SIZE = 1000;

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final String baseUrl;
  private final String serviceKey;

  public MolitTradeClient(
      @Value("${housing-lab.molit.base-url}") String baseUrl,
      @Value("${housing-lab.molit.api-key:}") String serviceKey) {
    this.baseUrl = baseUrl;
    this.serviceKey = serviceKey;
  }

  public boolean configured() { return !serviceKey.isBlank(); }

  public MolitResponseParser.Page fetch(PropertyType type, String regionCode, YearMonth month, int page) {
    // 포털이 주는 Decoding 키를 그대로 받아 여기서 한 번만 URL 인코딩한다.
    var uri = URI.create(baseUrl + "/" + type.path()
        + "?serviceKey=" + URLEncoder.encode(serviceKey, StandardCharsets.UTF_8)
        + "&LAWD_CD=" + regionCode
        + "&DEAL_YMD=" + month.format(DEAL_YMD)
        + "&pageNo=" + page
        + "&numOfRows=" + PAGE_SIZE);
    var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
    for (int attempt = 1; ; attempt++) {
      try {
        var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 500 && attempt < 3) { pause(attempt); continue; }
        if (response.statusCode() != 200) throw new MolitApiException("HTTP" + response.statusCode(), type.datasetId() + " 호출 실패");
        return MolitResponseParser.parse(response.body());
      } catch (IOException e) {
        if (attempt >= 3) throw new MolitApiException("IO", type.datasetId() + " 호출 실패: " + e.getMessage());
        pause(attempt);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new MolitApiException("INTERRUPTED", "수집이 중단되었습니다.");
      }
    }
  }

  private static void pause(int attempt) {
    try {
      Thread.sleep(1000L * attempt);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
