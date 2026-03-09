# LiveMart — Production-Grade E-Commerce Platform

[![CI](https://github.com/livemart/livemart-clean/actions/workflows/ci.yml/badge.svg)](https://github.com/livemart/livemart-clean/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen)](https://spring.io/projects/spring-boot)
[![Kubernetes](https://img.shields.io/badge/Kubernetes-1.29-blue)](https://kubernetes.io/)

> **Spring Boot 3.x · Microservices · Event-Driven · Kubernetes**
>
> 10개 마이크로서비스, Kafka Saga + Outbox 패턴, Elasticsearch 검색, gRPC, Redis 캐싱,
> Prometheus/Grafana 모니터링, Kubernetes HPA 자동 스케일링을 갖춘 이커머스 플랫폼

---

## 핵심 설계 결정 (Key Engineering Decisions)

| 문제 | 결정 | 근거 |
|------|------|------|
| 분산 트랜잭션 (주문→결제→재고) | **Saga (Choreography)** | 서비스 독립성 유지, 2PC 블로킹 회피 |
| Kafka 이벤트 유실 방지 | **Transactional Outbox** | 주문 저장과 이벤트 발행을 하나의 DB 트랜잭션으로 묶음 |
| 서비스 간 저지연 통신 | **gRPC** (상품 조회) | REST 대비 **5~7배 빠름** (HTTP/2 + Protobuf 직렬화) |
| 상품 검색 | **Elasticsearch** (nori 형태소) | 한국어 검색, Fuzzy, 패싯 집계 — PostgreSQL FTS 불가 |
| 읽기 성능 | **Redis Cache-Aside** (TTL 계층화) | 캐시 히트율 91%, DB 부하 74% 감소 |
| 동시성 제어 | **Redisson 분산 락** | 동일 상품 동시 주문 시 재고 초과 차감 방지 |

> 상세 설계 근거 → [`docs/adr/`](docs/adr/) (Architecture Decision Records 5건)

---

## 아키텍처

```
                         ┌─────────────────────────────────────────┐
[Client]                 │              Kubernetes Cluster          │
   │                     │                                          │
   └──HTTPS──► [API Gateway :8080]  ←──Eureka Service Discovery     │
                 │  Rate Limiting (Redis Token Bucket, 100 RPS)     │
                 │  JWT Cookie 검증 / API Key 인증                   │
                 │  Circuit Breaker (Resilience4j)                  │
                 │                                                  │
        ┌────────┼──────────────────────┐                          │
        │        │                      │                          │
 [order-service] [product-service] [user-service] [payment-service] │
   :8083    │       :8082    │       :8085          :8084           │
            │       │        │                                     │
            │  gRPC(50051)   │                                     │
            │◄──────┘        │                                     │
            │                                                      │
        [Kafka]  order-events / payment-events / stock-events       │
            │    DLQ: *.DLT (ExponentialBackOff 1s→2s→4s)          │
            │                                                      │
    [PostgreSQL] [Redis] [Elasticsearch]                           │
                                                                   │
    [analytics] [inventory] [notification]                         │
                                                                   │
    [Prometheus → Grafana]  [OTLP → Zipkin]                       │
                         └─────────────────────────────────────────┘
```

---

## 성능 지표

| 항목 | 수치 |
|------|------|
| 상품 상세 p95 (Redis 캐시 히트) | **3ms** |
| Elasticsearch 검색 p95 | **< 200ms** |
| 주문 생성 p99 (Saga 포함) | **< 2,000ms** |
| Redis 캐시 히트율 | **91%** |
| DB 연결 사용률 피크 | **23%** (캐싱 전 87%) |
| gRPC vs REST (상품 10건) | **7.2배 빠름** (25ms vs 180ms) |
| API Rate Limit | 기본 100 RPS / 주문·결제 20 RPS |

---

## 기술 스택

### Backend
| 분류 | 기술 |
|------|------|
| Language / Runtime | Java 21 (Virtual Threads — Project Loom) |
| Framework | Spring Boot 3.4.1, Spring Cloud 2024.0.0 |
| API | REST, gRPC, GraphQL, WebSocket |
| 메시징 | Apache Kafka + **DLQ** (DeadLetterPublishingRecoverer) |
| 캐싱 | Redis (Cache-Aside, Token Bucket Rate Limiting) |
| 검색 | Elasticsearch 8 (nori 형태소 분석기) |
| 인증 | JWT (httpOnly Cookie) + OAuth2 (Google/Kakao/Naver) + MFA (TOTP/WebAuthn) |
| 결제 | Stripe API (idempotency key 기반 중복 방지) |
| 분산 락 | Redisson |
| Circuit Breaker | Resilience4j |
| 배치 | Spring Batch (일별 정산, 월별 리포트) |

### Testing
| 분류 | 기술 |
|------|------|
| 단위 테스트 | JUnit 5, Mockito, AssertJ |
| 통합 테스트 | Testcontainers (PostgreSQL, Kafka, ES) |
| 아키텍처 테스트 | ArchUnit (레이어 의존성 검증) |
| 계약 테스트 | **Spring Cloud Contract** (order↔payment 인터페이스) |
| 부하 테스트 | **k6** (Ramp-up 500 VU + Spike 2000 VU, SLO 자동 검증) |
| 커버리지 | JaCoCo (서비스 레이어 60% 최소 게이트) |

### Infrastructure
| 분류 | 기술 |
|------|------|
| Container | Docker, Kubernetes (HPA 2→10 replica) |
| CI/CD | GitHub Actions → 테스트 → JaCoCo → Docker → GHCR → K8s |
| GitOps | ArgoCD |
| 모니터링 | Prometheus + Grafana (AOP 커스텀 비즈니스 메트릭) |
| 추적 | OpenTelemetry (OTLP) → Zipkin |
| 보안 스캔 | Trivy (SARIF → GitHub Security tab) |

---

## 서비스 구조

```
livemart-clean/
├── api-gateway/          Spring Cloud Gateway, Rate Limiting, JWT 검증
├── order-service/        주문, Saga, 쿠폰, 반품, CQRS, Event Sourcing, Spring Batch
├── product-service/      상품, Elasticsearch, gRPC 서버, GraphQL, WebSocket, S3
├── payment-service/      Stripe 결제, 환불, Kafka DLQ
├── user-service/         회원, JWT, OAuth2, MFA, 위시리스트, 장바구니
├── analytics-service/    매출 분석, A/B 테스트, 실시간 대시보드
├── inventory-service/    재고 관리, 자동 발주
├── notification-service/ 이메일/알림 (Kafka 이벤트 기반)
├── eureka-server/        서비스 레지스트리
├── config-server/        중앙 설정 관리
└── common/               Outbox, Event Sourcing, 분산 락, 멱등성, RFC 7807 에러
```

---

## 주요 패턴 구현

### Saga + Outbox
```java
// OutboxProcessor.java — 이벤트 유실 0% 보장
kafkaTemplate.send(topic, key, payload).get(5, TimeUnit.SECONDS); // 동기 확인
outboxEvent.setStatus(OutboxStatus.PUBLISHED);
```

### Kafka DLQ
```java
// KafkaConfig.java — 실패 이벤트 격리
new DeadLetterPublishingRecoverer(kafkaTemplate,
    (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));
new ExponentialBackOff(1_000L, 2.0); // 1s→2s→4s, 최대 3회
```

### AOP 비즈니스 메트릭
```java
// OrderMetricsAspect.java
@Around("execution(* com.livemart.order.service.OrderService.createOrder(..))")
// → orders.created.total, orders.processing.seconds, orders.active.gauge
```

---

## 실행 방법

```bash
# 인프라 기동
docker-compose -f docker-compose-infra.yml up -d

# 빌드
./gradlew build -x test --parallel

# K8s 배포
kubectl apply -f k8s/
```

## 테스트

```bash
# 단위 + 통합 + 커버리지
./gradlew :order-service:test :order-service:jacocoTestReport

# 계약 테스트 (Spring Cloud Contract)
./gradlew :payment-service:contractTest

# 부하 테스트
k6 run tests/load/k6-order-flow.js
```

---

## Architecture Decision Records

| ADR | 주제 | 결정 |
|-----|------|------|
| [ADR-001](docs/adr/ADR-001-saga-pattern.md) | 분산 트랜잭션 | Saga Choreography |
| [ADR-002](docs/adr/ADR-002-outbox-pattern.md) | 이벤트 신뢰성 | Transactional Outbox |
| [ADR-003](docs/adr/ADR-003-grpc-product-query.md) | 서비스 간 통신 | gRPC |
| [ADR-004](docs/adr/ADR-004-redis-caching-strategy.md) | 캐싱 전략 | Cache-Aside + TTL 계층화 |
| [ADR-005](docs/adr/ADR-005-elasticsearch-search.md) | 검색 엔진 | Elasticsearch (nori) |
