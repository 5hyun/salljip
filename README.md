# 우리 살 집 — 우리 가족, 어디서 살까?

“그때 내 돈으로 어떤 집을 살 수 있었고, 집을 샀다면 vs 전세로 살며 투자했다면 지금 자산은 얼마나 달라졌을까?”를 재현 가능한 계산으로 비교하는 프로젝트입니다.

## 현재 구현

- 현재 실거래 지도: 지도 이동 범위 조회, 줌별 지역 집계/단지 마커, 매매·전세·월세와 가격·면적 필터
- 단지 상세: 최신 거래, 최근 신고 내역, 가격 추이, 데이터 기준일·출처
- 타임머신 지도: 연·월/자기자본/가정 대출 설정, 예산 내 관측 단지, 최근 유사면적 가격과 변화율 비교
- 현재↔과거 전환 시 지도 중심·확대 수준·선택 단지·면적 조건 유지
- 데스크톱 3단 지도 레이아웃과 모바일 필터 Drawer·상세 Bottom Sheet
- 과거 기간·지역·예산·면적 기반 거래 관측 후보 검색
- 후보별 과거/최근 유사면적 가격 분포, 표본 수, 신뢰도, 전체 후보 변화 분포
- 네이버 지도 Web Dynamic Map 벡터 지도와 반응형 후보 목록
- 원금균등 대출, 취득·보유비, 전세보증금, 월세, 대체 투자 수익률 월별 현금흐름
- KOSPI·NASDAQ·S&P 500·예금·적금 금융자산 비교 UI/API(권리 확인 전 `INTERNAL_TEST` 합성값만 제공)
- 계산 결과에만 근거한 재현 가능한 해설과 근거·주의사항 UI/API
- 시장지표 API, 데이터 권리 상태 API, PostgreSQL/PostGIS 스키마
- 거래 정정 이력, 수집 실행, 라이선스 레지스트리, readiness, 입력 검증
- 계산 엔진 20개 골든 케이스 및 경계값 테스트
- Docker Compose 웹/API/PostGIS 실행

## 실행

### 로컬 개발 (맥): DB만 Docker, API·웹은 직접 실행

Docker Desktop을 켠 뒤 터미널 3개에서 각각 실행합니다.

```bash
npm run dev:db    # PostGIS 컨테이너만 (localhost:15432)
npm run dev:api   # Spring Boot bootRun (localhost:18080, 루트 .env의 API 키 사용)
npm run dev:web   # Next.js dev 서버 (localhost:3000, apps/web/.env.local 사용)
```

`dev:db`는 `docker-compose.dev.yml`을 함께 적용해 db 포트를 `127.0.0.1:15432`로 엽니다(기본 compose의 backend 네트워크는 internal이라 포트가 노출되지 않음). `scripts/dev-api.sh`가 루트 `.env`를 읽은 뒤 DB 주소만 `localhost:15432`로 바꿔 API를 띄웁니다. 웹 코드는 저장 즉시 반영되고, API는 재시작하면 반영됩니다. DB 종료는 `docker compose stop db`입니다.

### 전체 Docker (홈서버 배포와 동일)

```bash
cp .env.example .env
docker compose up --build
```

웹은 http://localhost:3000, API 상태는 http://localhost:18080/actuator/health/readiness 에서 확인합니다. 기본 API 포트는 기존 Jenkins의 8080 포트와 충돌하지 않도록 18080을 사용합니다.

## 홈서버 배포 (192.168.0.10)

```bash
deploy/deploy-all.sh   # db → api → web 전체
deploy/deploy-web.sh   # 웹만 (NEXT_PUBLIC_* 변경 시에도 이것으로 재빌드)
deploy/deploy-api.sh   # API만
deploy/deploy-db.sh    # DB만 (데이터는 볼륨에 유지)
deploy/status.sh       # 상태, `deploy/status.sh logs api` 로그
```

현재 작업 트리를 rsync로 서버 `~/apps/salljip`에 올린 뒤 서버에서 `docker compose up -d --build`를 실행합니다. 서버 설정은 서버의 `.env`(템플릿: `deploy/server.env.example`)에만 있고 동기화되지 않습니다. 웹 http://192.168.0.10:13000, API http://192.168.0.10:18080.

## 검증

```bash
npm run verify
```

지도·단지 상세·과거 검색의 거래 가격은 PostGIS에 적재한 국토교통부 매매 실거래만 사용하며 데모 모드는 없습니다. 전월세는 아직 수집하지 않아 가격 없이 표시됩니다. 시장지표·금융자산 비교값은 아직 실제 소스가 연결되지 않은 합성값입니다. 실제 데이터가 아니며 투자·대출·법률 판단에 사용할 수 없습니다. 외부 데이터는 `docs/licenses/README.md`의 권리 게이트 통과 전 공개 응답에서 차단합니다.

기본 지도는 네이버 지도 Web Dynamic Map의 GL 벡터 지도를 사용하며 일반·지형·위성 스타일을 전환할 수 있습니다. 브라우저에는 Client ID만 제공하며 Client Secret은 서버와 프런트엔드 코드에 저장하지 않습니다. 실제 과금과 무료 이용량은 네이버 클라우드 Maps 요금 정책을 따릅니다.

단지 목록과 위치는 수집한 실거래의 건물을 브이월드 지오코더로 좌표화한 것입니다(`IngestionRunner`, `MOLIT_API_KEY`·`VWORLD_API_KEY` 필요). DB가 비어 있으면 지도도 비어 있습니다. 거래 관측이 없는 단지는 가격을 추정하지 않고 지도에서 중립 점과 `최근 거래 없음` 상태로 표시합니다.
