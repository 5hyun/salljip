-- 국토교통부 실거래가(아파트·연립다세대·오피스텔) 적재를 위한 확장
ALTER TABLE buildings ADD COLUMN property_type VARCHAR(10) NOT NULL DEFAULT 'APT';
ALTER TABLE buildings ADD COLUMN umd_name VARCHAR(50);
ALTER TABLE buildings ADD COLUMN jibun VARCHAR(30);
ALTER TABLE buildings ADD COLUMN road_address VARCHAR(100);
ALTER TABLE buildings ADD COLUMN built_year INTEGER;
ALTER TABLE buildings ADD COLUMN geocode_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE buildings ALTER COLUMN location DROP NOT NULL;
ALTER TABLE buildings ALTER COLUMN addr_main DROP NOT NULL;
CREATE INDEX idx_buildings_region_type ON buildings(region_code, property_type);

CREATE UNIQUE INDEX uq_trades_fingerprint ON trades(source_fingerprint);
CREATE INDEX idx_trades_region_area_date ON trades(region_code, exclusive_area_m2, contract_date);

INSERT INTO regions(region_code, name, parent_code) VALUES
  ('11110', '서울특별시 종로구', '11'), ('11140', '서울특별시 중구', '11'),
  ('11170', '서울특별시 용산구', '11'), ('11200', '서울특별시 성동구', '11'),
  ('11215', '서울특별시 광진구', '11'), ('11230', '서울특별시 동대문구', '11'),
  ('11260', '서울특별시 중랑구', '11'), ('11290', '서울특별시 성북구', '11'),
  ('11305', '서울특별시 강북구', '11'), ('11320', '서울특별시 도봉구', '11'),
  ('11350', '서울특별시 노원구', '11'), ('11380', '서울특별시 은평구', '11'),
  ('11410', '서울특별시 서대문구', '11'), ('11440', '서울특별시 마포구', '11'),
  ('11470', '서울특별시 양천구', '11'), ('11500', '서울특별시 강서구', '11'),
  ('11530', '서울특별시 구로구', '11'), ('11545', '서울특별시 금천구', '11'),
  ('11560', '서울특별시 영등포구', '11'), ('11590', '서울특별시 동작구', '11'),
  ('11620', '서울특별시 관악구', '11'), ('11650', '서울특별시 서초구', '11'),
  ('11680', '서울특별시 강남구', '11'), ('11710', '서울특별시 송파구', '11'),
  ('11740', '서울특별시 강동구', '11')
ON CONFLICT (region_code) DO NOTHING;

-- 공공데이터포털 상세 페이지 표기 기준(2026-11-19 확인): 비용 무료, 이용허락범위 제한 없음
INSERT INTO source_licenses(source_id, provider, dataset, permission, allowed_public, allowed_commercial, attribution, license_url, reviewed_at, notes) VALUES
  ('MOLIT_APT_TRADE', '국토교통부', '아파트 매매 실거래가 상세 자료', 'PUBLIC_ALLOWED', TRUE, TRUE,
   '출처: 국토교통부 실거래가 공개시스템(공공데이터포털)', 'https://www.data.go.kr/data/15126468/openapi.do', DATE '2026-11-19', '이용허락범위 제한 없음'),
  ('MOLIT_RH_TRADE', '국토교통부', '연립다세대 매매 실거래가 자료', 'PUBLIC_ALLOWED', TRUE, TRUE,
   '출처: 국토교통부 실거래가 공개시스템(공공데이터포털)', 'https://www.data.go.kr/data/15126467/openapi.do', DATE '2026-11-19', '이용허락범위 제한 없음'),
  ('MOLIT_OFFI_TRADE', '국토교통부', '오피스텔 매매 실거래가 자료', 'PUBLIC_ALLOWED', TRUE, TRUE,
   '출처: 국토교통부 실거래가 공개시스템(공공데이터포털)', 'https://www.data.go.kr/data/15126464/openapi.do', DATE '2026-11-19', '이용허락범위 제한 없음'),
  ('VWORLD_GEOCODER', '국토교통부', '지오코더 API(브이월드)', 'REVIEWING', FALSE, FALSE,
   '좌표: 브이월드 지오코더', 'https://www.data.go.kr/data/15101106/openapi.do', DATE '2026-11-19',
   '포털 표기는 제한 없음. 좌표 영구 저장 조건은 브이월드 약관 재확인 필요')
ON CONFLICT (source_id) DO NOTHING;
