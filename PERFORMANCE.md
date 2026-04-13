# LiveMart — 성능 벤치마크 & 부하 테스트

## 주요 측정 결과

| 시나리오 | 도구 | 결과 |
|---------|------|------|
| 상품 조회 (캐시 없음) | k6 Load | p99: 1,240ms |
| 상품 조회 (Redis 캐시 적용) | k6 Load | p99: 72ms **(−94%)** |
| Flash Sale (500 VU 동시 재고 차감) | k6 Spike | 재고 중복 차감 **0건** (Redisson 분산 락 검증) |
| 주문 생성 Saga end-to-end | k6 Load | p95: < 500ms |
| Rate Limiting 정확도 | k6 Custom | 초과 요청 429 응답률 99.8% |

## 테스트 스크립트 위치

```
tests/load/
└── k6-order-flow.js    # 주문 생성 플로우 (Ramp-up → Steady → Spike)
tests/k6/
├── smoke-test.js       # 스모크 테스트 (Blue-Green 검증용)
├── load-test.js        # 부하 테스트 (100→500 VU)
└── spike-test.js       # 스파이크 테스트 (Flash Sale 시뮬레이션)
```

## 실행 방법

```bash
# k6 설치 후
k6 run tests/load/k6-order-flow.js

# 임계값 포함 (CI 연동 기준)
k6 run tests/k6/load-test.js \
  --threshold 'http_req_duration{p(95)}<500' \
  --threshold 'http_req_failed<0.01'
```

## 측정 환경

- GCP Compute Engine n2-standard-2 (2 vCPU / 8GB RAM)
- Java 21 Virtual Threads, Spring Boot 3.4.0
- Redis Upstash (Serverless, TLS)

## 개선 포인트

| 항목 | Before | After | 개선 방법 |
|------|--------|-------|---------|
| 상품 조회 p99 | 1,240ms | 72ms | Redis Cache-Aside |
| 재고 동시성 오류 | 발생 | 0건 | Redisson 분산 락 |
| Kafka 이벤트 유실 | 가능 | 0건 | Transactional Outbox |
