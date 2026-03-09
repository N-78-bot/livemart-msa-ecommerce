# ADR-002: Kafka 이벤트 신뢰성을 위한 Transactional Outbox 패턴

- **상태**: 채택됨 (Accepted)
- **날짜**: 2026-01-20
- **결정자**: 백엔드 팀

## 배경 (Context)

`order-service`가 주문을 DB에 저장한 후 Kafka에 이벤트를 발행하는 두 연산은 원자성이 보장되지 않는다.

```java
// 문제 시나리오
orderRepository.save(order);      // ① DB 저장 성공
kafkaTemplate.send("order-events", event); // ② Kafka 실패 → 이벤트 유실!
```

Kafka 발행 실패 시 재고 차감, 결제 처리가 트리거되지 않아 데이터 불일치 발생.

## 결정 (Decision)

**Transactional Outbox Pattern** 채택:

```
[동일 트랜잭션]
  1. orders 테이블에 주문 INSERT
  2. outbox_events 테이블에 이벤트 INSERT

[별도 스레드 - OutboxProcessor]
  3. outbox_events 폴링 (1초 간격)
  4. Kafka 발행 성공 → status = PUBLISHED
  5. 발행 실패 → 재시도 (지수 백오프)
```

## 채택 이유 (Rationale)

- **Atomicity**: 주문 저장과 이벤트 저장이 하나의 DB 트랜잭션
- **At-Least-Once**: OutboxProcessor가 미발행 이벤트를 재처리
- **Idempotency**: consumer 측에서 `event_id` 기반 중복 처리 방지

## At-Least-Once vs Exactly-Once 비교

| 전략 | 구현 복잡도 | 성능 | 선택 이유 |
|------|------------|------|----------|
| At-Least-Once + Idempotent Consumer | 중간 | 높음 | ✅ 채택 |
| Exactly-Once (Kafka Transaction) | 높음 | 낮음 (2PC와 유사) | ❌ 오버엔지니어링 |

## 트레이드오프

**장점:**
- 이벤트 유실 0% 보장
- DB와 동일한 ACID 트랜잭션 활용
- Kafka 장애 시에도 주문 처리 계속됨

**단점:**
- outbox_events 테이블 폴링에 의한 DB 부하 (1초 간격, 인덱스로 최소화)
- 이벤트 처리에 최대 1~2초 지연 발생 가능

## 구현 위치

- `common/outbox/OutboxEvent.java` — outbox_events 테이블 엔티티
- `common/outbox/OutboxProcessor.java` — 스케줄링 + Kafka 동기 발행 (`kafkaTemplate.send().get(5s)`)
- `common/outbox/OutboxPublisher.java` — 서비스에서 이벤트 저장 헬퍼
- DB 마이그레이션: `V3__create_outbox_events.sql`
