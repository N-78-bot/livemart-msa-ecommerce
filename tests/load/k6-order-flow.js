/**
 * LiveMart E-Commerce — k6 Load Test Suite
 *
 * 시나리오: 실제 유저 행동 패턴 기반 부하 테스트
 *   1. 회원가입 / 로그인
 *   2. 상품 목록 조회 + 검색 (Elasticsearch)
 *   3. 상품 상세 조회 (Redis 캐시 히트 측정)
 *   4. 주문 생성 (Saga 패턴 — Kafka 이벤트 체인)
 *   5. 결제 처리 (Stripe 연동)
 *
 * 목표 SLO:
 *   - p95 응답시간 < 200ms (상품 조회)
 *   - p99 응답시간 < 1000ms (주문 생성)
 *   - 에러율 < 1%
 *   - RPS 500 (안정 구간), 1000 (피크)
 *
 * 실행:
 *   k6 run tests/load/k6-order-flow.js
 *   k6 run --out influxdb=http://localhost:8086/k6 tests/load/k6-order-flow.js
 */

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { randomIntBetween, randomItem } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// ── 커스텀 메트릭 ──────────────────────────────────────────────────
const orderCreationTime  = new Trend('order_creation_time_ms', true);
const paymentTime        = new Trend('payment_time_ms', true);
const searchTime         = new Trend('search_time_ms', true);
const cacheHitRate       = new Rate('cache_hit_rate');
const orderSuccessRate   = new Rate('order_success_rate');
const sagaCompletionRate = new Rate('saga_completion_rate');

// ── 설정 ───────────────────────────────────────────────────────────
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    // 시나리오 1: 점진적 부하 증가 (Ramp-up)
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50   },  // 워밍업
        { duration: '1m',  target: 200  },  // 정상 부하
        { duration: '2m',  target: 500  },  // 목표 부하
        { duration: '1m',  target: 1000 },  // 피크 부하
        { duration: '30s', target: 0    },  // 쿨다운
      ],
      gracefulRampDown: '30s',
    },

    // 시나리오 2: 스파이크 테스트 (Flash Sale 시뮬레이션)
    spike_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 10   },
        { duration: '5s',  target: 2000 },  // 갑작스러운 스파이크
        { duration: '30s', target: 2000 },
        { duration: '10s', target: 0    },
      ],
      startTime: '6m',  // ramp_up 종료 후 실행
      gracefulRampDown: '10s',
    },
  },

  // SLO 임계값 — 이 값을 넘으면 CI fail
  thresholds: {
    http_req_failed:                   ['rate<0.01'],      // 에러율 1% 미만
    http_req_duration:                 ['p(95)<500'],      // p95 < 500ms
    'http_req_duration{name:search}':  ['p(95)<200'],      // 검색 p95 < 200ms
    'http_req_duration{name:product}': ['p(95)<100'],      // 상품조회 p95 < 100ms (캐시)
    'http_req_duration{name:order}':   ['p(99)<2000'],     // 주문생성 p99 < 2s (Saga)
    order_success_rate:                ['rate>0.99'],      // 주문 성공률 99%+
    saga_completion_rate:              ['rate>0.95'],      // Saga 완료율 95%+
  },
};

// ── 공통 헤더 ──────────────────────────────────────────────────────
const JSON_HEADERS = {
  'Content-Type': 'application/json',
  'Accept': 'application/json',
};

// 테스트 상품 ID 풀 (사전 시딩 필요)
const PRODUCT_IDS   = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
const SEARCH_TERMS  = ['노트북', '스마트폰', '이어폰', '키보드', '마우스', '모니터'];
const CATEGORIES    = ['electronics', 'fashion', 'food', 'sports'];

// ── 메인 시나리오 ──────────────────────────────────────────────────
export default function () {
  const userId = `test_user_${__VU}_${__ITER}`;
  let authCookies = null;

  // 1. 인증 (로그인)
  group('Authentication', function () {
    const loginRes = http.post(
      `${BASE_URL}/api/users/login`,
      JSON.stringify({
        email: `${userId}@loadtest.com`,
        password: 'LoadTest1234!',
      }),
      {
        headers: JSON_HEADERS,
        tags: { name: 'login' },
      }
    );

    const loginOk = check(loginRes, {
      'login: status 200':       r => r.status === 200,
      'login: has userId cookie': r => r.cookies['userId'] !== undefined || r.json('userId') !== undefined,
    });

    if (loginOk) {
      authCookies = loginRes.cookies;
    }
    sleep(0.5);
  });

  // 2. 상품 목록 조회 (캐시 히트 측정)
  group('Product Browse', function () {
    const productListRes = http.get(
      `${BASE_URL}/api/products?page=0&size=20&category=${randomItem(CATEGORIES)}`,
      {
        headers: JSON_HEADERS,
        cookies: authCookies,
        tags: { name: 'product' },
      }
    );

    check(productListRes, {
      'product list: status 200':       r => r.status === 200,
      'product list: has content':      r => r.json('content') !== null,
      'product list: cache header set': r => r.headers['X-Cache'] !== undefined,
    });

    // 캐시 히트 여부 (X-Cache: HIT)
    cacheHitRate.add(productListRes.headers['X-Cache'] === 'HIT');

    // 상품 상세 (Redis 캐시)
    const productId = randomItem(PRODUCT_IDS);
    const productDetailRes = http.get(
      `${BASE_URL}/api/products/${productId}`,
      {
        headers: JSON_HEADERS,
        tags: { name: 'product' },
      }
    );

    check(productDetailRes, {
      'product detail: status 200': r => r.status === 200,
      'product detail: has price':  r => r.json('price') > 0,
    });

    sleep(randomIntBetween(1, 3));
  });

  // 3. Elasticsearch 검색
  group('Search', function () {
    const keyword = randomItem(SEARCH_TERMS);
    const searchStart = Date.now();

    const searchRes = http.get(
      `${BASE_URL}/api/products/search?keyword=${encodeURIComponent(keyword)}&page=0&size=10`,
      {
        headers: JSON_HEADERS,
        tags: { name: 'search' },
      }
    );

    const elapsed = Date.now() - searchStart;
    searchTime.add(elapsed);

    check(searchRes, {
      'search: status 200':      r => r.status === 200,
      'search: has results':     r => Array.isArray(r.json('content')),
      'search: p95 < 200ms':     () => elapsed < 200,
    });

    sleep(1);
  });

  // 4. 주문 생성 (Saga 패턴)
  group('Order Creation (Saga)', function () {
    if (!authCookies) {
      return; // 미인증 시 스킵
    }

    const productId = randomItem(PRODUCT_IDS);
    const orderStart = Date.now();

    const orderRes = http.post(
      `${BASE_URL}/api/orders`,
      JSON.stringify({
        userId: __VU,
        items: [
          { productId: productId, quantity: 1 }
        ],
        deliveryAddress: '서울시 강남구 테헤란로 123',
        phoneNumber: '010-1234-5678',
        paymentMethod: 'CREDIT_CARD',
        couponCode: null,
      }),
      {
        headers: JSON_HEADERS,
        cookies: authCookies,
        tags: { name: 'order' },
        timeout: '10s',
      }
    );

    const orderElapsed = Date.now() - orderStart;
    orderCreationTime.add(orderElapsed);

    const orderOk = check(orderRes, {
      'order: status 201':         r => r.status === 201,
      'order: has orderNumber':     r => r.json('orderNumber') !== null,
      'order: created within 2s':  () => orderElapsed < 2000,
    });
    orderSuccessRate.add(orderOk);

    // 5. Saga 완료 확인 (비동기 처리 대기)
    if (orderOk) {
      const orderNumber = orderRes.json('orderNumber');
      let sagaCompleted = false;

      // Saga 완료를 최대 5초 폴링 (Kafka 비동기 처리)
      for (let i = 0; i < 5; i++) {
        sleep(1);
        const statusRes = http.get(
          `${BASE_URL}/api/orders/${orderRes.json('id')}`,
          {
            headers: JSON_HEADERS,
            cookies: authCookies,
            tags: { name: 'order_status' },
          }
        );

        const status = statusRes.json('status');
        if (status === 'CONFIRMED' || status === 'PAYMENT_COMPLETED') {
          sagaCompleted = true;
          break;
        }
      }
      sagaCompletionRate.add(sagaCompleted);
    }

    sleep(randomIntBetween(2, 5));
  });
}

// ── 테스트 결과 요약 ───────────────────────────────────────────────
export function handleSummary(data) {
  const summary = {
    timestamp: new Date().toISOString(),
    environment: BASE_URL,
    slo_results: {
      error_rate_under_1pct:    data.metrics.http_req_failed?.values?.rate < 0.01,
      p95_under_500ms:          data.metrics.http_req_duration?.values?.['p(95)'] < 500,
      search_p95_under_200ms:   data.metrics['http_req_duration{name:search}']?.values?.['p(95)'] < 200,
      order_success_over_99pct: data.metrics.order_success_rate?.values?.rate > 0.99,
      saga_completion_over_95pct: data.metrics.saga_completion_rate?.values?.rate > 0.95,
    },
    key_metrics: {
      total_requests:           data.metrics.http_reqs?.values?.count,
      rps_avg:                  data.metrics.http_reqs?.values?.rate?.toFixed(2),
      error_rate:               (data.metrics.http_req_failed?.values?.rate * 100)?.toFixed(2) + '%',
      p50_ms:                   data.metrics.http_req_duration?.values?.['p(50)']?.toFixed(0),
      p95_ms:                   data.metrics.http_req_duration?.values?.['p(95)']?.toFixed(0),
      p99_ms:                   data.metrics.http_req_duration?.values?.['p(99)']?.toFixed(0),
      order_creation_p99_ms:    data.metrics.order_creation_time_ms?.values?.['p(99)']?.toFixed(0),
      search_p95_ms:            data.metrics.search_time_ms?.values?.['p(95)']?.toFixed(0),
      cache_hit_rate:           (data.metrics.cache_hit_rate?.values?.rate * 100)?.toFixed(1) + '%',
    },
  };

  return {
    'tests/load/results/summary.json': JSON.stringify(summary, null, 2),
    stdout: `
╔══════════════════════════════════════════════════════════╗
║              LiveMart Load Test Results                  ║
╠══════════════════════════════════════════════════════════╣
║  Total Requests : ${String(summary.key_metrics.total_requests).padEnd(38)}║
║  Avg RPS        : ${String(summary.key_metrics.rps_avg + ' req/s').padEnd(38)}║
║  Error Rate     : ${String(summary.key_metrics.error_rate).padEnd(38)}║
║  P50 Latency    : ${String(summary.key_metrics.p50_ms + 'ms').padEnd(38)}║
║  P95 Latency    : ${String(summary.key_metrics.p95_ms + 'ms').padEnd(38)}║
║  P99 Latency    : ${String(summary.key_metrics.p99_ms + 'ms').padEnd(38)}║
║  Order P99      : ${String(summary.key_metrics.order_creation_p99_ms + 'ms').padEnd(38)}║
║  Search P95     : ${String(summary.key_metrics.search_p95_ms + 'ms').padEnd(38)}║
║  Cache Hit Rate : ${String(summary.key_metrics.cache_hit_rate).padEnd(38)}║
╠══════════════════════════════════════════════════════════╣
║                    SLO Compliance                        ║
║  Error < 1%     : ${summary.slo_results.error_rate_under_1pct   ? '✅ PASS' : '❌ FAIL'}                                 ║
║  P95 < 500ms    : ${summary.slo_results.p95_under_500ms          ? '✅ PASS' : '❌ FAIL'}                                 ║
║  Search < 200ms : ${summary.slo_results.search_p95_under_200ms   ? '✅ PASS' : '❌ FAIL'}                                 ║
║  Order > 99%    : ${summary.slo_results.order_success_over_99pct ? '✅ PASS' : '❌ FAIL'}                                 ║
║  Saga > 95%     : ${summary.slo_results.saga_completion_over_95pct ? '✅ PASS' : '❌ FAIL'}                               ║
╚══════════════════════════════════════════════════════════╝
    `,
  };
}
