package com.housinglab.api.ingestion;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 브이월드 지오코더: 주소 → WGS84 좌표. */
@Component
public class VworldGeocoder {
  private static final Pattern X = Pattern.compile("\"x\"\\s*:\\s*\"([0-9.]+)\"");
  private static final Pattern Y = Pattern.compile("\"y\"\\s*:\\s*\"([0-9.]+)\"");
  private static final Pattern STATUS = Pattern.compile("\"status\"\\s*:\\s*\"([A-Z_]+)\"");
  private static final Pattern ERROR = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");

  public record Point(double longitude, double latitude) {}

  public enum AddressType { PARCEL, ROAD }

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final String apiKey;

  public VworldGeocoder(@Value("${housing-lab.vworld.api-key:}") String apiKey) { this.apiKey = apiKey.trim(); }

  public boolean configured() { return !apiKey.isBlank(); }

  /** 주소를 찾지 못하면 빈 값, 키·한도 오류는 예외. */
  public Optional<Point> geocode(String address, AddressType type) {
    var uri = URI.create("https://api.vworld.kr/req/address?service=address&request=getcoord&version=2.0"
        + "&crs=epsg:4326&format=json&refine=true&simple=true"
        + "&type=" + type.name().toLowerCase()
        + "&address=" + URLEncoder.encode(address, StandardCharsets.UTF_8)
        + "&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
    String body = send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build());
    return parse(body);
  }

  static Optional<Point> parse(String body) {
    var status = STATUS.matcher(body);
    String code = status.find() ? status.group(1) : "UNKNOWN";
    if ("NOT_FOUND".equals(code)) return Optional.empty();
    if (!"OK".equals(code)) {
      var error = ERROR.matcher(body);
      throw new IllegalStateException("브이월드 지오코더 오류(" + code + "): " + (error.find() ? error.group(1) : body));
    }
    var x = X.matcher(body);
    var y = Y.matcher(body);
    if (!x.find() || !y.find()) return Optional.empty();
    return Optional.of(new Point(Double.parseDouble(x.group(1)), Double.parseDouble(y.group(1))));
  }

  private String send(HttpRequest request) {
    for (int attempt = 1; ; attempt++) {
      try {
        var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 200) return response.body();
        if (attempt >= 3) throw new IllegalStateException("브이월드 HTTP " + response.statusCode());
      } catch (IOException e) {
        if (attempt >= 3) throw new IllegalStateException("브이월드 호출 실패: " + e.getMessage());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("지오코딩이 중단되었습니다.");
      }
      try {
        Thread.sleep(1000L * attempt);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
