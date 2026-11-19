#!/usr/bin/env bash
# 공통 배포 함수. deploy-*.sh 에서 source 해서 사용한다.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEPLOY_HOST="${DEPLOY_HOST:-internal-ip}"           # ~/.ssh/config 의 Host (192.168.0.10)
DEPLOY_DIR="${DEPLOY_DIR:-~/apps/housing-opportunity-lab}"
SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=8)

log() { printf '\033[1;32m[deploy]\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m[deploy]\033[0m %s\n' "$*" >&2; exit 1; }

remote() { ssh "${SSH_OPTS[@]}" "$DEPLOY_HOST" "$@"; }

ensure_server() {
  if ! remote true 2>/dev/null; then
    log "서버 응답 없음 → 깨우는 중 (ping)"
    ping -c 3 192.168.0.10 >/dev/null 2>&1 || true
    remote true || die "서버($DEPLOY_HOST)에 SSH 접속 실패"
  fi
  remote "test -f $DEPLOY_DIR/.env" \
    || die "서버에 $DEPLOY_DIR/.env 가 없습니다. deploy/server.env.example 을 참고해 먼저 만드세요."
}

sync_source() {
  log "소스 동기화 → $DEPLOY_HOST:$DEPLOY_DIR"
  remote "mkdir -p $DEPLOY_DIR"
  rsync -az --delete \
    --exclude '.git/' --exclude 'node_modules/' --exclude '.next/' \
    --exclude 'build/' --exclude '.gradle/' --exclude 'output/' --exclude '.21st/' \
    --exclude '.env' --exclude '.env.*' --exclude '.DS_Store' \
    -e "ssh ${SSH_OPTS[*]}" \
    "$ROOT_DIR/" "$DEPLOY_HOST:$DEPLOY_DIR/"
}

compose() { remote "cd $DEPLOY_DIR && docker compose $*"; }

# deploy_service <service> [compose up 추가 옵션...]
deploy_service() {
  local svc="$1"; shift
  ensure_server
  sync_source
  log "$svc 빌드/재시작"
  compose up -d --build "$@" "$svc"
  wait_healthy "$svc"
}

wait_healthy() {
  local svc="$1" id status i
  id="$(compose ps -q "$svc")"
  [ -n "$id" ] || die "$svc 컨테이너를 찾을 수 없음"
  for i in $(seq 1 60); do
    status="$(remote "docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $id")"
    case "$status" in
      healthy|running) log "$svc: $status"; return 0 ;;
      unhealthy|exited|dead) compose logs --tail 50 "$svc"; die "$svc: $status" ;;
    esac
    sleep 3
  done
  compose logs --tail 50 "$svc"
  die "$svc: 헬스체크 시간 초과 ($status)"
}
