package com.housinglab.api.ingestion;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 일회성 수집 실행. 예:
 * {@code java -jar app.jar --housing-lab.ingest.region=11350 --housing-lab.ingest.from=2006-01}
 * 수집 후 좌표 없는 건물을 지오코딩하고({@code --housing-lab.ingest.geocode=false}로 끔) 프로세스를 종료한다.
 */
@Component
@ConditionalOnProperty(name = "housing-lab.ingest.region")
public class IngestionRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(IngestionRunner.class);

  private final TradeIngestionService service;
  private final GeocodingService geocoding;
  private final ConfigurableApplicationContext context;
  private final String region;
  private final String from;
  private final String to;
  private final String types;
  private final int refreshRecentMonths;
  private final boolean skipTrades;
  private final boolean geocode;

  public IngestionRunner(
      TradeIngestionService service,
      GeocodingService geocoding,
      ConfigurableApplicationContext context,
      @Value("${housing-lab.ingest.region}") String region,
      @Value("${housing-lab.ingest.from:2006-01}") String from,
      @Value("${housing-lab.ingest.to:}") String to,
      @Value("${housing-lab.ingest.types:APT,RH,OFFI}") String types,
      @Value("${housing-lab.ingest.refresh-recent-months:3}") int refreshRecentMonths,
      @Value("${housing-lab.ingest.skip-trades:false}") boolean skipTrades,
      @Value("${housing-lab.ingest.geocode:true}") boolean geocode) {
    this.skipTrades = skipTrades;
    this.geocode = geocode;
    this.service = service;
    this.geocoding = geocoding;
    this.context = context;
    this.region = region;
    this.from = from;
    this.to = to;
    this.types = types;
    this.refreshRecentMonths = refreshRecentMonths;
  }

  @Override
  public void run(ApplicationArguments args) {
    var end = to.isBlank() ? YearMonth.now(java.time.ZoneId.of("Asia/Seoul")) : YearMonth.parse(to);
    List<PropertyType> selected = Arrays.stream(types.split(",")).map(String::trim).map(PropertyType::valueOf).toList();
    int exitCode = 0;
    try {
      if (!skipTrades) {
        var summaries = service.ingest(region, YearMonth.parse(from), end, selected, refreshRecentMonths);
        for (var s : summaries) {
          log.info("[수집 결과] {} 수집 {}개월 / 건너뜀 {}개월 / 받음 {}건 / 신규 {}건 / 해제 갱신 {}건{}",
              s.type(), s.months(), s.skippedMonths(), s.fetched(), s.inserted(), s.updated(),
              s.error() == null ? "" : " / 오류: " + s.error());
          if (s.error() != null) exitCode = 1;
        }
      }
      if (geocode) {
        int[] result = geocoding.geocodePending(region);
        log.info("[지오코딩 결과] 좌표 확보 {}곳 / 주소 미발견 {}곳", result[0], result[1]);
      }
    } catch (RuntimeException e) {
      log.error("수집 실패: {}", e.getMessage());
      exitCode = 1;
    }
    int code = exitCode;
    System.exit(SpringApplication.exit(context, () -> code));
  }
}
