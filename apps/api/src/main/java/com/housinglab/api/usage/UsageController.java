package com.housinglab.api.usage;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 브라우저가 직접 부르는 외부 API(네이버 지도)를 프론트가 알려주면 기록한다. */
@RestController
@RequestMapping("/api/usage")
public class UsageController {
  private final ApiUsageRecorder recorder;

  public UsageController(ApiUsageRecorder recorder) { this.recorder = recorder; }

  @PostMapping("/map-load")
  ResponseEntity<Void> mapLoad() {
    recorder.success(ApiProvider.NAVER_MAP);
    return ResponseEntity.noContent().build();
  }
}
