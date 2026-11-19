#!/usr/bin/env bash
# 전체 배포: db → api → web 순서
source "$(dirname "$0")/lib.sh"
ensure_server
sync_source
for svc in db api web; do
  log "$svc 빌드/재시작"
  compose up -d --build --no-deps "$svc"
  wait_healthy "$svc"
done
compose ps
