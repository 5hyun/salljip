package com.housinglab.api.catalog;

import static com.housinglab.api.catalog.CatalogModels.Building;

import com.fasterxml.jackson.databind.JsonNode;
import com.housinglab.api.usage.ApiProvider;
import com.housinglab.api.usage.ApiUsageRecorder;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ApartmentCatalogService {
  private static final String NOWON_REGION_CODE = "11350";
  private static final int PAGE_SIZE = 1_000;
  private static final Duration CACHE_TTL = Duration.ofHours(24);

  private final DemoCatalog demoCatalog;
  private final RestClient restClient;
  private final String apiKey;
  private final ApiUsageRecorder usage;
  private volatile List<Building> cachedNowon = List.of();
  private volatile Instant cachedAt = Instant.EPOCH;

  public ApartmentCatalogService(
      DemoCatalog demoCatalog,
      @Value("${seoul.open-data-key:}") String apiKey,
      ApiUsageRecorder usage) {
    this.demoCatalog = demoCatalog;
    this.usage = usage;
    this.restClient = RestClient.builder().baseUrl("http://openapi.seoul.go.kr:8088").build();
    this.apiKey = apiKey.trim();
  }

  public List<Building> buildings(String regionCode) {
    if (!NOWON_REGION_CODE.equals(regionCode) || apiKey.isBlank()) {
      return demoCatalog.buildings().stream()
          .filter(building -> building.regionCode().equals(regionCode))
          .toList();
    }

    if (!cachedNowon.isEmpty() && cachedAt.plus(CACHE_TTL).isAfter(Instant.now())) return cachedNowon;
    synchronized (this) {
      if (!cachedNowon.isEmpty() && cachedAt.plus(CACHE_TTL).isAfter(Instant.now())) return cachedNowon;
      try {
        cachedNowon = loadNowonApartments();
        cachedAt = Instant.now();
      } catch (RuntimeException ignored) {
        if (cachedNowon.isEmpty()) {
          cachedNowon = demoCatalog.buildings().stream()
              .filter(building -> building.regionCode().equals(NOWON_REGION_CODE))
              .toList();
        }
      }
      return cachedNowon;
    }
  }

  private List<Building> loadNowonApartments() {
    var apartmentsByName = new LinkedHashMap<String, Building>();
    var firstPage = fetchPage(1, PAGE_SIZE);
    addNowonRows(firstPage.path("row"), apartmentsByName);
    int total = firstPage.path("list_total_count").asInt();
    for (int start = PAGE_SIZE + 1; start <= total; start += PAGE_SIZE) {
      addNowonRows(fetchPage(start, Math.min(start + PAGE_SIZE - 1, total)).path("row"), apartmentsByName);
    }

    demoCatalog.buildings().stream()
        .filter(building -> building.regionCode().equals(NOWON_REGION_CODE))
        .forEach(building -> apartmentsByName.put(normalize(building.name()), building));
    return List.copyOf(apartmentsByName.values());
  }

  private JsonNode fetchPage(int start, int end) {
    JsonNode response;
    try {
      response = restClient.get()
          .uri("/{key}/json/OpenAptInfo/{start}/{end}/", apiKey, start, end)
          .retrieve()
          .body(JsonNode.class);
    } catch (RuntimeException e) {
      usage.failure(ApiProvider.SEOUL_OPEN_DATA);
      throw e;
    }
    if (response == null || !response.has("OpenAptInfo")) {
      usage.failure(ApiProvider.SEOUL_OPEN_DATA);
      throw new IllegalStateException("서울시 공동주택 데이터를 불러오지 못했습니다.");
    }
    usage.success(ApiProvider.SEOUL_OPEN_DATA);
    return response.path("OpenAptInfo");
  }

  private void addNowonRows(JsonNode rows, Map<String, Building> apartmentsByName) {
    if (!rows.isArray()) return;
    var parsed = new ArrayList<Building>();
    rows.forEach(row -> {
      if (!"노원구".equals(row.path("SGG_ADDR").asText())) return;
      double longitude = row.path("XCRD").asDouble();
      double latitude = row.path("YCRD").asDouble();
      String name = row.path("APT_NM").asText().trim();
      if (name.isBlank() || longitude < 126 || longitude > 128 || latitude < 37 || latitude > 38) return;
      String id = "seoul-" + row.path("APT_CD").asText().trim().toLowerCase(Locale.ROOT);
      String address = row.path("APT_RDN_ADDR").asText().trim();
      int households = (int) Math.round(row.path("TNOHSH").asDouble());
      int builtYear = parseYear(row.path("USE_APRV_YMD").asText());
      parsed.add(new Building(id, NOWON_REGION_CODE, name, address, longitude, latitude, households, builtYear));
    });
    parsed.forEach(building -> apartmentsByName.putIfAbsent(normalize(building.name()), building));
  }

  private int parseYear(String value) {
    if (value == null || value.length() < 10) return 0;
    try {
      return LocalDate.parse(value.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE).getYear();
    } catch (RuntimeException ignored) {
      return 0;
    }
  }

  private String normalize(String name) {
    return name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
  }
}
