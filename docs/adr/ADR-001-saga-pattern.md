# ADR-001: 분산 트랜잭션에 Saga 패턴 (Choreography) 채택

- **상태**: 채택됨 (Accepted)
- **날짜**: 2026-01-15
- **결정자**: 백엔드 팀

## 배경 (Context)

주문 생성 시 order-service, payment-service, product-service 3개 서비스에 걸친 데이터 정합성이 필요하다.
전통적인 2PC(Two-Phase Commit)는 마이크로서비스 환경에서 다음 문제를 야기한다:
- 서비스 간 강한 결합 (tight coupling)
- 코디네이터 장애 시 전체 시스템 블로킹
- 네트워크 파티션에 취약

## 결정 (Decision)

**Choreography 기반 Saga 패턴**을 채택한다.

```
OrderService       PaymentService      ProductService
     │                    │                   │
  주문생성                 │                   │
     │──ORDER_CREATED──►  │                   │
     │                 결제처리               │
     │                    │──PAYMENT_COMPLETED►│
     │                    │               재고차감
     │◄──ORDER_CONFIRMED──│                   │
```

보상 트랜잭션 (실패 시):
```
결제 실패 → PAYMENT_FAILED → order-service: 주문 취소
재고 부족 → STOCK_FAILED  → payment-service: 환불 처리
```

## 채택 이유 (Rationale)

| 항목 | 2PC | Saga (Choreography) |
|------|-----|---------------------|
| 결합도 | 강함 | 느슨함 (이벤트 기반) |
| 가용성 | 낮음 (블로킹) | 높음 (비동기) |
| 복잡도 | 낮음 | 중간 (보상 로직 필요) |
| 확장성 | 낮음 | 높음 |

## 트레이드오프 (Consequences)

**장점:**
- 각 서비스가 독립적으로 배포/확장 가능
- 특정 서비스 장애가 전체 시스템을 블로킹하지 않음
- Kafka Outbox 패턴으로 최소 1회 전달 보장

**단점:**
- 최종 일관성(Eventual Consistency) — 짧은 시간 동안 데이터 불일치 존재
- 보상 트랜잭션 로직 복잡도 증가
- 디버깅 시 분산 추적(OpenTelemetry) 필수

## 구현 위치

- `order-service/event/OrderPaymentConsumer.java` — PAYMENT_COMPLETED 처리
- `payment-service/event/PaymentEventConsumer.java` — ORDER_CREATED 처리
- `product-service/event/ProductEventConsumer.java` — 재고 차감 + 롤백
- `common/outbox/OutboxProcessor.java` — 이벤트 신뢰성 보장
