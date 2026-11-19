#!/usr/bin/env bash
# Spring Boot API 배포 (DB 가 꺼져 있으면 함께 기동, 웹은 건드리지 않음)
source "$(dirname "$0")/lib.sh"
deploy_service api
