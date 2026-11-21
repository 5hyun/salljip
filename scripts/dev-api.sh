#!/usr/bin/env bash
# 맥 로컬 개발용: Docker의 db(localhost:${DB_PORT:-15432})에 붙어 API를 직접 실행한다.
# 외부 API 키 등은 루트 .env 에서 읽고, Docker 전용 값(DATABASE_URL 등)만 로컬용으로 덮어쓴다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

# db 컨테이너(docker-compose.yml)와 같은 계정으로 맞춘다.
export DATABASE_URL="jdbc:postgresql://localhost:${DB_PORT:-15432}/${POSTGRES_DB:-housing_lab}"
export DATABASE_USERNAME="${POSTGRES_USER:-housing_lab}"
export DATABASE_PASSWORD="${POSTGRES_PASSWORD:-housing_lab}"
# 웹이 기본으로 호출하는 포트(18080)와 맞춘다.
export SERVER_PORT="${API_PORT:-18080}"

cd "$ROOT/apps/api"
exec ./gradlew bootRun
