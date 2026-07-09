// Stage 7 — 대기열 관통 부하 측정.
// 유저 N명이 동시에 대기열에 진입하고, 순번을 폴링하다 입장권을 받으면 주문까지 관통한다.
// 관측: 진입 지연, 입장권 수령까지 대기시간, 주문 지연·성공/실패, (외부 수집기로) 대기 인원 드레인·Hikari·CPU.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_COUNT = Number(__ENV.USER_COUNT || 300);
const PRODUCT_COUNT = Number(__ENV.PRODUCT_COUNT || 10); // 1이면 단일 상품 핫로우 회차
const PRODUCT_ID_BASE = Number(__ENV.PRODUCT_ID_BASE || 1);
const LOGIN_PW = __ENV.LOGIN_PW || 'Looptest1234';
const POLL_INTERVAL_SECONDS = Number(__ENV.POLL_INTERVAL || 1);
const POLL_LIMIT = Number(__ENV.POLL_LIMIT || 120);

const enterLatency = new Trend('queue_enter_latency', true);
const pollLatency = new Trend('queue_poll_latency', true);
const orderLatency = new Trend('order_latency', true);
const tokenWaitSeconds = new Trend('token_wait_seconds');
const orderCreated = new Counter('order_created');
const orderRejected = new Counter('order_rejected');
const pollTimeout = new Counter('poll_timeout');

export const options = {
    scenarios: {
        rush: {
            executor: 'per-vu-iterations',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: '5m',
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
    },
};

function authHeaders(loginId, extraHeaders = {}) {
    return {
        headers: {
            'X-Loopers-LoginId': loginId,
            'X-Loopers-LoginPw': LOGIN_PW,
            ...extraHeaders,
        },
    };
}

export default function () {
    const loginId = `loop${__VU}`;

    const enterResponse = http.post(`${BASE_URL}/api/v1/queue/enter`, null, authHeaders(loginId));
    enterLatency.add(enterResponse.timings.duration);
    const entered = check(enterResponse, {
        'entered with 201': (response) => response.status === 201,
    });
    if (!entered) {
        return;
    }
    const enteredAt = Date.now();

    let entryToken = null;
    for (let attempt = 0; attempt < POLL_LIMIT; attempt++) {
        const pollResponse = http.get(`${BASE_URL}/api/v1/queue/position`, authHeaders(loginId));
        pollLatency.add(pollResponse.timings.duration);
        if (pollResponse.status === 200) {
            const token = pollResponse.json('data.entryToken');
            if (token) {
                entryToken = token;
                break;
            }
        }
        sleep(POLL_INTERVAL_SECONDS);
    }
    if (entryToken === null) {
        pollTimeout.add(1);
        return;
    }
    tokenWaitSeconds.add((Date.now() - enteredAt) / 1000);

    const productId = PRODUCT_ID_BASE + (__VU % PRODUCT_COUNT);
    const orderBody = JSON.stringify({
        items: [{ productId: productId, quantity: 1 }],
        userCouponId: null,
    });
    const orderResponse = http.post(`${BASE_URL}/api/v1/orders`, orderBody, authHeaders(loginId, {
        'Content-Type': 'application/json',
        'X-Entry-Token': entryToken,
    }));
    orderLatency.add(orderResponse.timings.duration);
    const ordered = check(orderResponse, {
        'ordered with 201': (response) => response.status === 201,
    });
    if (ordered) {
        orderCreated.add(1);
    } else {
        orderRejected.add(1);
    }
}
