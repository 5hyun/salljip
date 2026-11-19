# 데이터·소프트웨어 권리 레지스트리

`모름`은 공개 허용이 아니라 차단을 뜻한다. 실데이터 어댑터는 아래 표가 `PUBLIC_ALLOWED` 이상일 때만 사용자 응답에 포함한다.

| 소스 | 현재 상태 | 공개 | 상업 | 메모 |
|---|---|---:|---:|---|
| 국토교통부 아파트 매매 API | 검토 중 | 아니오 | 아니오 | 데모는 합성 데이터만 사용 |
| 서울시 공동주택 아파트 정보 | 연결 완료 | 예 | 예 | 공공누리 1유형, 출처표시, 단지 위치·기본정보만 사용 |
| 네이버 지도 Web Dynamic Map | 연결 완료 | 약관 범위 | 약관 범위 | Client ID·허용 URL 인증, 사용량 기반 과금, 네이버 기본 표시 유지 |
| OpenFreeMap 벡터 타일 | 예비 구현 | 예 | 예 | MIT 프로젝트, OSM/OpenMapTiles 표시 필수, 공개 인스턴스 SLA 없음 |
| Protomaps 자체 호스팅 | 운영 후보 | 예 | 예 | ODbL Produced Work, OSM 표시 필수, 저장소·CDN 직접 운영 |
| KRX/KOSPI | BLOCKED | 아니오 | 아니오 | 비상업 조건 확인 필요 |
| FRED S&P500/NASDAQ | BLOCKED | 아니오 | 아니오 | 지수 권리자·재배포 조건 확인 필요 |
| 한국은행 ECOS 환율 | 검토 중 | 아니오 | 아니오 | 개별 사용조건 확인 필요 |
| 금융상품한눈에 | 검토 중 | 아니오 | 아니오 | 역사 상품 데이터 확보 여부 별도 |

OSS는 Next.js(MIT), MapLibre GL JS(BSD-3-Clause), Apache ECharts(Apache-2.0), Spring Boot(Apache-2.0), PostgreSQL(PostgreSQL License), PostGIS(GPL-2.0-or-later)을 사용한다. 배포 전 고정 버전의 전이 의존성을 다시 스캔한다.

## 지도 타일 운영 게이트

기본 지도는 네이버 지도 Web Dynamic Map의 GL 벡터 지도를 사용한다. 브라우저에는 `NEXT_PUBLIC_NAVER_MAP_CLIENT_ID`만 노출하고 Client Secret은 절대 포함하지 않는다. 네이버 로고·데이터 출처·기본 지도 표시는 SDK가 제공하는 상태를 유지하며, Web 서비스 URL과 Dynamic Map 상품을 콘솔에서 활성화해야 한다. 호출량과 과금은 네이버 클라우드 Maps 정책을 따른다. OpenFreeMap·Protomaps는 네이버 장애나 공급자 전환이 필요한 경우를 위한 예비 후보로만 유지한다.

- 네이버 지도 JavaScript SDK: https://navermaps.github.io/maps.js.en/docs/tutorial-2-Getting-Started.html
- 네이버 Maps 서비스 환경·인증: https://guide.ncloud-docs.com/docs/en/maps-app/
- 서울시 공동주택 아파트 정보: https://data.seoul.go.kr/dataList/OA-15818/A/1/datasetView.do
- 네이버 Maps 요금: https://www.ncloud.com/charge/price/ko
- OpenFreeMap 사용·라이선스: https://openfreemap.org/
- OpenFreeMap 이용약관: https://openfreemap.org/tos/
- Protomaps 배포·자체 호스팅 안내: https://docs.protomaps.com/basemaps/downloads
