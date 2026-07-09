#!/usr/bin/env bash
# Stage 7 대기열 부하 측정용 시드.
# commerce-api(local, ddl-auto:create) 부팅 직후 1회 실행한다.
#   1) 회원가입 API 로 측정 유저 N명 생성 → 평문 비밀번호 확보(bcrypt 해시 역산 회피)
#   2) 브랜드 1개 + 주문 대상 상품 P개(재고 넉넉히)를 raw SQL 로 적재
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-docker-mysql-1}"
USER_COUNT="${USER_COUNT:-300}"
LOGIN_PW="${LOGIN_PW:-Looptest1234}"
PRODUCT_COUNT="${PRODUCT_COUNT:-10}"
STOCK="${STOCK:-1000000}"

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

echo "[seed] 브랜드 1개 + 상품 ${PRODUCT_COUNT}개 적재 (재고 ${STOCK})"
docker exec -i "${MYSQL_CONTAINER}" mysql -uroot -proot loopers <<SQL
INSERT INTO brands (name, description, created_at, updated_at)
VALUES ('측정 브랜드', '부하 측정용 브랜드', NOW(), NOW());
SET @brand_id = LAST_INSERT_ID();
INSERT INTO products (brand_id, name, description, price, stock, like_count, created_at, updated_at)
SELECT @brand_id, CONCAT('측정 상품 ', seq.n), '부하 측정용 상품', 39000, ${STOCK}, 0, NOW(), NOW()
FROM (SELECT ROW_NUMBER() OVER () AS n FROM information_schema.columns LIMIT ${PRODUCT_COUNT}) seq;
SELECT MIN(id) AS product_id_base, COUNT(*) AS product_count FROM products;
SQL

echo "[seed] 완료"
