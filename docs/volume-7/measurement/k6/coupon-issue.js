// Stage 12 — 선착순 쿠폰 발급 동시성 측정.
// 유저 300명이 수량 100장 한정 쿠폰에 동시에 발급 요청(접수 즉시 응답)을 보내고,
// requestId 로 결과를 polling 해 SUCCESS/FAILED 분포를 수집한다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const COUPON_ID = __ENV.COUPON_ID || '1';
const USER_COUNT = Number(__ENV.USER_COUNT || 300);
const LOGIN_PW = __ENV.LOGIN_PW || 'Looptest1234';
const POLL_INTERVAL_SECONDS = 0.5;
const POLL_LIMIT = 60;

const acceptLatency = new Trend('issue_accept_latency', true);
const acceptedCount = new Counter('issue_accepted');
const successCount = new Counter('issue_success');
const failedCount = new Counter('issue_failed');
const pendingTimeoutCount = new Counter('issue_pending_timeout');

export const options = {
    scenarios: {
        rush: {
            executor: 'per-vu-iterations',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: '3m',
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
    },
};

function authHeaders(loginId) {
    return {
        headers: {
            'X-Loopers-LoginId': loginId,
            'X-Loopers-LoginPw': LOGIN_PW,
        },
    };
}

export default function () {
    const loginId = `loop${__VU}`;

    const acceptResponse = http.post(`${BASE_URL}/api/v1/coupons/${COUPON_ID}/issue`, null, authHeaders(loginId));
    acceptLatency.add(acceptResponse.timings.duration);
    const accepted = check(acceptResponse, {
        'accepted with 202': (response) => response.status === 202,
        'has requestId': (response) => response.json('data.requestId') !== undefined,
    });
    if (!accepted) {
        return;
    }
    acceptedCount.add(1);

    const requestId = acceptResponse.json('data.requestId');
    for (let attempt = 0; attempt < POLL_LIMIT; attempt++) {
        const pollResponse = http.get(`${BASE_URL}/api/v1/coupons/issue/${requestId}`, authHeaders(loginId));
        const status = pollResponse.json('data.status');
        if (status === 'SUCCESS') {
            successCount.add(1);
            return;
        }
        if (status === 'FAILED') {
            failedCount.add(1);
            return;
        }
        sleep(POLL_INTERVAL_SECONDS);
    }
    pendingTimeoutCount.add(1);
}
