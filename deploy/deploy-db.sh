#!/usr/bin/env bash
# PostGIS DB 배포 (데이터는 docker 볼륨 housing_lab_db 에 유지됨)
source "$(dirname "$0")/lib.sh"
deploy_service db
