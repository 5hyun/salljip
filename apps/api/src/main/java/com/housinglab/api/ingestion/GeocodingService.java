package com.housinglab.api.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 좌표가 없는 건물을 지번(없으면 도로명) 주소로 지오코딩해 buildings.location 을 채운다. */
@Service
@ConditionalOnProperty(name = "housing-lab.demo-mode", havingValue = "false")
public class GeocodingService {
  private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

  private final VworldGeocoder geocoder;
  private final JdbcTemplate jdbc;

  public GeocodingService(VworldGeocoder geocoder, JdbcTemplate jdbc) {
    this.geocoder = geocoder;
    this.jdbc = jdbc;
  }

  private record Pending(String id, String region, String umd, String jibun, String road) {}

  /** @return [성공, 주소 없음] */
  public int[] geocodePending(String regionCode) {
    if (!geocoder.configured()) throw new IllegalStateException("VWORLD_API_KEY 가 설정되지 않았습니다.");
    var pending = jdbc.query("""
        SELECT b.id, r.name, b.umd_name, b.jibun, b.road_address
          FROM buildings b JOIN regions r ON r.region_code = b.region_code
         WHERE b.region_code = ? AND b.location IS NULL AND b.geocode_status = 'PENDING'""",
        (rs, i) -> new Pending(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)),
        regionCode);
    log.info("지오코딩 대상 {}곳", pending.size());
    int ok = 0, missing = 0, consecutiveErrors = 0;
    for (var building : pending) {
      java.util.Optional<VworldGeocoder.Point> point;
      try {
        point = building.jibun() == null ? java.util.Optional.empty()
            : geocoder.geocode(building.region() + " " + building.umd() + " " + building.jibun(), VworldGeocoder.AddressType.PARCEL);
        if (point.isEmpty() && building.road() != null) {
          point = geocoder.geocode(building.region() + " " + building.road(), VworldGeocoder.AddressType.ROAD);
        }
        consecutiveErrors = 0;
      } catch (IllegalStateException e) {
        // 일시 오류는 PENDING으로 남겨 다음 실행에서 다시 시도한다. 연속 실패(키·한도 문제)면 중단.
        log.warn("지오코딩 실패({}): {}", building.id(), e.getMessage());
        if (++consecutiveErrors >= 10) throw e;
        continue;
      }
      if (point.isPresent()) {
        jdbc.update("UPDATE buildings SET location = ST_SetSRID(ST_MakePoint(?, ?), 4326), geocode_status = 'OK' WHERE id = CAST(? AS UUID)",
            point.get().longitude(), point.get().latitude(), building.id());
        ok++;
      } else {
        jdbc.update("UPDATE buildings SET geocode_status = 'NOT_FOUND' WHERE id = CAST(? AS UUID)", building.id());
        missing++;
      }
      if ((ok + missing) % 200 == 0) log.info("지오코딩 진행 {}/{}", ok + missing, pending.size());
    }
    return new int[] {ok, missing};
  }
}
