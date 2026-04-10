# ADR-008: 주문 생성 API 멱등성 키(Idempotency Key) 도입

- **상태**: 채택됨 (Accepted)
- **날짜**: 2026-02-20
- **결정자**: 백엔드 팀

## 배경 (Context)

네트워크 타임아웃, 브라우저 새로고침, 결제 PG 콜백 중복 등으로 인해 POST `/orders` 요청이 중복 실행될 수 있다.
중복 주문이 생성되면:
- 재고가 이중 차감됨
- 사용자에게 이중 결제가 청구됨
- Saga 흐름에서 보상 트랜잭션 처리 비용 증가

기존 DB UNIQUE 제약만으로는 클라이언트가 동일 요청을 재전송했을 때 방어하기 어렵다.

## 결정 (Decision)

클라이언트가 `Idempotency-Key` HTTP 헤더로 UUID를 전달하면 서버에서 24시간 동안 동일 요청의 결과를 캐싱한다.

**구현:**
- `@IdempotencyKey(prefix, ttlSeconds)` AOP 어노테이션
- Redis에 `idempotency:{prefix}:{key}` 형태로 응답 직렬화 저장
- 동일 키 재요청 시 Redis에서 이전 응답 반환 (DB/서비스 로직 미실행)
- TTL: 24시간 (`ttlSeconds = 86400`)

```
POST /api/orders
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
```

**Redis 키 구조:**
```
idempotency:order-create:550e8400-e29b-41d4-a716-446655440000 → {serialized response}
TTL: 86400s
```

## 검토한 대안

| 대안 | 이유로 미채택 |
|---|---|
| DB UNIQUE(orderNumber) 만 사용 | 클라이언트 재전송 시 400 에러 반환 — UX 불량, 네트워크 이슈와 중복 구분 불가 |
| 세션 기반 중복 방지 | MSA 수평 확장 시 세션 공유 문제 |
| DB 비관적 락 | 락 경합 시 타임아웃 가능, Redisson 분산 락과 혼용 시 데드락 위험 |

## 결과 (Consequences)

**긍정적:**
- Stripe 등 PG사 표준 패턴과 일치 — API 설계 신뢰성 향상
- 네트워크 재시도 안전 보장 (클라이언트 SDK/BFF에서 자동 재시도 가능)
- 주문 생성 외 다른 멱등성이 필요한 엔드포인트에도 동일 어노테이션 재사용 가능

**부정적:**
- Redis 의존성 추가 (장애 시 멱등성 체크 우회 또는 전체 주문 실패 선택 필요)
- 클라이언트가 UUID를 생성·관리해야 함 (헤더 누락 시 멱등성 미보장)
