#!/usr/bin/env bash
# 서버 상태/로그 확인. 사용: deploy/status.sh [logs <service>]
source "$(dirname "$0")/lib.sh"
if [ "${1:-}" = "logs" ]; then
  compose logs --tail "${TAIL:-100}" -f "${2:-}"
else
  compose ps
fi
