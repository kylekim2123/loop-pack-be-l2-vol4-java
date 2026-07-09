#!/usr/bin/env bash
# Stage 7 측정 중 서버·Redis 지표를 0.5초 간격 CSV 로 수집한다.
# 사용: ./collect-metrics.sh <수집시간(초)> <출력파일.csv>
#   - waiting        : Redis ZCARD waiting-queue (대기 인원 → 드레인 기울기)
#   - hikari_active  : 사용 중 DB 커넥션
#   - hikari_pending : 커넥션 대기 스레드 (0이 아니면 풀 포화 신호)
#   - process_cpu    : 앱 프로세스 CPU 사용률 (0~1)
#   - system_cpu     : 시스템 전체 CPU 사용률 (0~1)
set -euo pipefail

DURATION="${1:?수집시간(초)을 지정하세요}"
OUTPUT="${2:?출력 CSV 경로를 지정하세요}"
ACTUATOR_URL="${ACTUATOR_URL:-http://localhost:8081/actuator/prometheus}"
REDIS_CONTAINER="${REDIS_CONTAINER:-redis-master}"

metric_value() {
    echo "$1" | grep "^$2" | head -1 | awk '{print $NF}'
}

echo "epoch_ms,waiting,hikari_active,hikari_pending,process_cpu,system_cpu" > "${OUTPUT}"
END=$((SECONDS + DURATION))
while (( SECONDS < END )); do
    EPOCH_MS=$(python3 -c 'import time; print(int(time.time()*1000))')
    WAITING=$(docker exec "${REDIS_CONTAINER}" redis-cli ZCARD waiting-queue 2>/dev/null || echo -1)
    METRICS=$(curl -s --max-time 2 "${ACTUATOR_URL}" || echo "")
    HIKARI_ACTIVE=$(metric_value "${METRICS}" 'hikaricp_connections_active')
    HIKARI_PENDING=$(metric_value "${METRICS}" 'hikaricp_connections_pending')
    PROCESS_CPU=$(metric_value "${METRICS}" 'process_cpu_usage')
    SYSTEM_CPU=$(metric_value "${METRICS}" 'system_cpu_usage')
    echo "${EPOCH_MS},${WAITING},${HIKARI_ACTIVE:--1},${HIKARI_PENDING:--1},${PROCESS_CPU:--1},${SYSTEM_CPU:--1}" >> "${OUTPUT}"
    sleep 0.5
done
echo "[collect] 완료 → ${OUTPUT}"
