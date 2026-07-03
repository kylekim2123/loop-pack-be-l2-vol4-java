#!/usr/bin/env bash
# Stage 12 선착순 쿠폰 동시성 측정용 시드.
# commerce-api(local, ddl-auto:create) 부팅 직후 1회 실행한다.
#   1) 회원가입 API 로 측정 유저 N명 생성 → 평문 비밀번호 확보(bcrypt 해시 역산 회피)
#   2) 선착순 한정 쿠폰(max_quantity)을 raw SQL 로 적재
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-docker-mysql-1}"
USER_COUNT="${USER_COUNT:-300}"
LOGIN_PW="${LOGIN_PW:-Looptest1234}"
MAX_QUANTITY="${MAX_QUANTITY:-100}"

echo "[seed] 회원가입 ${USER_COUNT}명 (loop1..loop${USER_COUNT})"
for i in $(seq 1 "${USER_COUNT}"); do
    curl -s -o /dev/null -X POST "${BASE_URL}/api/v1/users" \
        -H 'Content-Type: application/json' \
        -d "{\"loginId\":\"loop${i}\",\"password\":\"${LOGIN_PW}\",\"name\":\"측정유저\",\"birthDate\":\"1990-01-01\",\"email\":\"loop${i}@loopers.test\"}" &
    if (( i % 30 == 0 )); then
        wait
        echo "[seed] ${i}명 완료"
    fi
done
wait

echo "[seed] 선착순 쿠폰 적재 (max_quantity=${MAX_QUANTITY})"
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot loopers <<SQL
INSERT INTO coupons (name, discount_type, discount_value, min_order_amount, expired_at, max_quantity, issued_count, created_at, updated_at)
VALUES ('선착순 한정 쿠폰', 'FIXED', 5000, 10000, DATE_ADD(NOW(), INTERVAL 7 DAY), ${MAX_QUANTITY}, 0, NOW(), NOW());
SELECT id AS coupon_id FROM coupons ORDER BY id DESC LIMIT 1;
SQL

echo "[seed] 완료"
