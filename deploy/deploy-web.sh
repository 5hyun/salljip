#!/usr/bin/env bash
# Next.js 웹 배포. NEXT_PUBLIC_* 값은 빌드 시점에 박히므로 서버 .env 수정 후엔 이 스크립트로 재빌드
source "$(dirname "$0")/lib.sh"
deploy_service web --no-deps
