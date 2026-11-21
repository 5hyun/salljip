-- 외부 API 사용량. 기관이 사용량 조회 API를 주지 않아 우리 백엔드가 호출할 때마다 직접 센다.
-- source: 호출한 기기(mac / minipc). 미니PC DB에는 Mac이 동기화로 보낸 행도 함께 쌓인다.
CREATE TABLE api_usage_daily (
  source VARCHAR(30) NOT NULL,
  provider VARCHAR(30) NOT NULL,
  usage_date DATE NOT NULL,
  calls INTEGER NOT NULL DEFAULT 0,
  errors INTEGER NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (source, provider, usage_date)
);

-- 보내는 쪽(Mac)의 동기화 시도 이력
CREATE TABLE usage_sync_log (
  id BIGSERIAL PRIMARY KEY,
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  manual BOOLEAN NOT NULL,
  success BOOLEAN NOT NULL,
  message VARCHAR(500)
);

-- 받는 쪽(미니PC)이 기기별로 마지막으로 받은 시각
CREATE TABLE usage_sync_received (
  source VARCHAR(30) PRIMARY KEY,
  received_at TIMESTAMPTZ NOT NULL,
  row_count INTEGER NOT NULL
);
