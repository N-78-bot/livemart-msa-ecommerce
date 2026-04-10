# ADR-007: 주문 서비스에 CQRS 패턴 적용

- **상태**: 채택됨 (Accepted)
- **날짜**: 2026-02-10
- **결정자**: 백엔드 팀

## 배경 (Context)

주문 서비스는 두 가지 상반된 요구사항을 동시에 처리해야 한다:

1. **쓰기(Command)**: 주문 생성·취소·상태 변경 — 재고 락, Outbox 이벤트 발행, 분산 트랜잭션 참여 등 복잡한 부수효과 수반
2. **읽기(Query)**: 주문 상세 조회, 목록 조회, 관리자 통계 — 빠른 응답과 캐싱이 중요하며 부수효과 없음

단일 서비스 클래스에서 두 책임을 혼합하면:
- 읽기 메서드에 쓰기 트랜잭션(`@Transactional`)이 의도치 않게 적용될 수 있음
- 캐싱 정책과 쓰기 로직이 뒤섞여 코드 가독성 하락
- 읽기/쓰기 부하를 독립적으로 스케일링하기 어려움

## 결정 (Decision)

주문 서비스의 읽기·쓰기 로직을 두 개의 독립 서비스 클래스로 분리한다:

| 클래스 | 역할 | 트랜잭션 |
|---|---|---|
| `OrderService` | 주문 생성·취소·상태 변경 (Command) | `@Transactional` |
| `OrderQueryService` | 조회·통계 (Query) | `@Transactional(readOnly = true)` |

**구현 세부:**
- `OrderQueryService`는 `readOnly = true`로 Hibernate dirty checking 비활성화 → 읽기 성능 개선
- `@Cacheable`은 Query 측에만 적용 (`order-detail`, `order-statistics` 캐시)
- 통계 쿼리(`getOrderStatistics`)는 `findAll()` 대신 DB 집계 함수(`COUNT`, `SUM`) 사용 — OOM 방지
- 읽기·쓰기 컨트롤러 라우팅은 동일 `OrderController`에서 처리 (API 인터페이스 변경 없음)

## 검토한 대안

| 대안 | 이유로 미채택 |
|---|---|
| 단일 OrderService | 트랜잭션·캐싱 책임 혼재, readOnly 최적화 불가 |
| 별도 read DB (물리적 CQRS) | 포트폴리오 규모에서 과도한 인프라 복잡도 |
| Event Sourcing + 별도 Read Model | Kafka Outbox 기반 이벤트로 향후 확장 가능하나 현 단계에서 불필요 |

## 결과 (Consequences)

**긍정적:**
- `readOnly` 트랜잭션으로 DB 부하 감소 (Hibernate Session flush mode = NEVER)
- 캐싱·조회 로직이 쓰기와 독립적으로 변경 가능
- 통계 API가 전체 엔티티 로딩 없이 DB SUM/COUNT로 처리 → 데이터 증가 시 OOM 위험 제거

**부정적:**
- 클래스 수 증가 (서비스 1개 → 2개)
- 쓰기 후 캐시 무효화(`@CacheEvict`) 누락 시 일관성 문제 가능 — 캐시 TTL로 완화
