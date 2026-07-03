# Stage 12 — 선착순 쿠폰 발급 동시성 측정

> 수량 100장 한정 쿠폰에 유저 300명이 동시에 발급을 요청했을 때,
> ① 수량 초과·중복 발급이 0건인지 ② Kafka가 버퍼로서 요청 폭주로부터 시스템을 지키는지 확인한다.

## 측정 환경

| 구성 | 값 |
|---|---|
| 실행 | commerce-api(8080) + commerce-streamer(8082) 로컬 bootRun, `local` 프로파일 |
| 인프라 | docker MySQL 8.0 · Redis 7.0 · Kafka 3.5.1(KRaft) |
| 토픽 | `coupon-issue-requests` — partitions=1, Consumer concurrency=1 (순차) |
| 쿠폰 | `max_quantity=100`, `issued_count=0`에서 시작 |
| 부하 | k6 `per-vu-iterations` — 300 VU × 1회 접수 후 0.5초 간격 polling |
| 시드 | 회원가입 API로 유저 300명(loop1~loop300) 생성 |

> 과제 기준(선착순 100명)을 넘는 최소 규모로 요청 수를 300으로 잡았다. 규모보다 "초과 0건"과 "접수/처리 분리"의 관찰이 목적이다.

## 결과 — 정확성 (초과·중복 0건)

| 항목 | 값 |
|---|---|
| 접수(202 + requestId) | **300 / 300 (100%)** |
| 최종 SUCCESS | **정확히 100** |
| 최종 FAILED | 200 (사유 전부 "쿠폰 수량이 모두 소진되었습니다.") |
| `coupons.issued_count` | 100 (= max_quantity, 초과 0) |
| `user_coupons` 행 수 / distinct user | 100 / 100 (중복 0) |
| polling 타임아웃(미종결) | 0 |
| Outbox 미발행 잔여 | 0 / 300 |

정확성은 Consumer의 **조건부 UPDATE**(`issued_count + 1 WHERE issued_count < max_quantity`)와 `UNIQUE(user_id, coupon_id)`가 담보했다. 파티션 순차(concurrency=1)는 처리 순서를 해석 가능하게 하는 보조 장치일 뿐, 100장을 가른 것은 DB의 원자적 CAS다.

## 결과 — Kafka 버퍼 효과 (접수와 처리의 분리)

```
접수(API, 302 requests/거의 동시)          처리(Consumer, 순차 소진)
00:12:57.3 ──── 00:13:02.2 (4.9초)        00:13:00.6 ──────────────── 00:13:42.6 (42초)
      ↑ 300건 전부 202 즉시 응답                ↑ 1건씩 자기 속도로 발급 확정
```

- **접수 창구는 4.9초 만에 300건을 전부 받아냈고**, 실제 발급은 Consumer가 42초에 걸쳐 순차 소진했다. 요청 폭주가 발급 트랜잭션(쿠폰 행 경합)으로 직결되지 않는다 — 폭주는 Kafka(정확히는 Outbox 큐)가 흡수한다.
- 접수 p95는 6.8초로 낮지 않은데, 이는 큐잉이 아니라 **요청마다 수행되는 BCrypt 인증**(헤더 기반 로그인)과 dev 모드 단일 인스턴스 특성이 지배한 값이다. 접수 로직 자체는 PENDING INSERT + Outbox INSERT 두 건이 전부다.
- polling까지 포함한 사용자 완주 시간(iteration)은 평균 33.9초 — 마지막 순번 유저는 컨슈머 소진 완료(42초)까지 대기했다. 결과 통지가 필요하면 polling 대신 push(웹소켓·알림)로 개선할 수 있으나 범위 밖.

## 판단

- **수량 초과 0건, 중복 0건** — 선착순 정확성은 Kafka 순서가 아니라 조건부 UPDATE + UNIQUE 제약이 담보함을 실측으로 확인.
- **버퍼 효과 확인** — 접수(4.9초)와 소진(42초)의 시간 분리가 명확하다. 동기 발급이었다면 300 트랜잭션이 같은 쿠폰 행 잠금을 두고 직렬화되며 API 응답이 그 꼬리를 그대로 물었을 것이다.
- 한계: 단일 인스턴스·로컬 측정이라 절대 수치보다 구조적 분리(접수/처리)와 정합성 결과로 읽어야 한다.

## 재현 방법

```bash
docker compose -f docker/infra-compose.yml up -d          # MySQL/Redis/Kafka
./gradlew :apps:commerce-api:bootRun                       # 8080 (토픽 자동 생성)
./gradlew :apps:commerce-streamer:bootRun --args='--server.port=8082 --management.server.port=8083'
bash docs/volume-7/measurement/k6/seed.sh                  # 유저 300 + 쿠폰(100장)
k6 run docs/volume-7/measurement/k6/coupon-issue.js
```
