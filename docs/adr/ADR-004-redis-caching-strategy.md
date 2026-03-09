# ADR-004: Redis 캐싱 전략 — Cache-Aside + TTL 계층화

- **상태**: 채택됨 (Accepted)
- **날짜**: 2026-02-10
- **결정자**: 백엔드 팀

## 배경 (Context)

상품 조회가 전체 트래픽의 73%를 차지한다. DB 직접 조회 시:
- 상품 목록: 평균 420ms (JOIN 쿼리, 페이지네이션)
- 상품 상세: 평균 85ms
- 카테고리: 변경 빈도 낮지만 모든 요청에 포함

DB 연결 풀 고갈로 피크 시간대 timeout 발생 (초당 200 req 이상).

## 결정 (Decision)

**Cache-Aside 패턴** + **TTL 계층화** 적용.

```
요청 → Redis 조회 → HIT: 즉시 반환
                  → MISS: DB 조회 → Redis 저장 → 반환
```

### TTL 전략

| 데이터 | TTL | 이유 |
|--------|-----|------|
| 상품 상세 (`product:{id}`) | 5분 | 가격/재고 변동 가능 |
| 상품 목록 (`products:page:{n}`) | 1시간 | 페이지 조합 비용 큼 |
| 카테고리 (`categories`) | 1시간 | 변경 빈도 매우 낮음 |
| 장바구니 세션 | 30분 | 사용자 세션과 연동 |

### 캐시 무효화

```java
@CacheEvict(value = "product-detail", key = "#productId")
public void updateProduct(Long productId, ...) { ... }
```
- 상품 수정/삭제 시 즉시 무효화
- 재고 변경(Kafka 이벤트) 시 `product:{id}` 무효화

## 채택 이유 (Rationale)

**Write-Through 미채택 이유:** 쓰기 작업이 드물고 캐시 오염 위험

**Write-Behind 미채택 이유:** 재고/가격 정합성이 중요 — 유실 불가

**Cache-Aside 선택 이유:**
- 읽기 우세 트래픽에 최적
- 캐시 장애 시 DB 폴백 자동 (resilience)
- 코드 단순성

## 성능 결과

| 지표 | 캐싱 전 | 캐싱 후 | 개선율 |
|------|---------|---------|--------|
| 상품 상세 p95 | 85ms | 3ms | **96%↓** |
| 상품 목록 p95 | 420ms | 12ms | **97%↓** |
| DB 연결 사용률 | 87% (피크) | 23% (피크) | **64%↓** |
| 캐시 히트율 | — | 91% | — |

## 트레이드오프

**장점:**
- DB 부하 74% 감소
- 응답속도 10~35배 향상
- Redis 장애 시 자동 폴백

**단점:**
- Stale Read: TTL 만료 전까지 오래된 데이터 반환 가능 (상품 상세 최대 5분)
- 캐시 워밍업 필요 (재시작 후 첫 요청들은 느림)
- 메모리 비용

## 구현 위치

- `product-service/config/RedisConfig.java` — ObjectMapper, TTL 설정
- `product-service/service/ProductService.java` — `@Cacheable`, `@CacheEvict`
- `common/cache/DistributedCacheService.java` — 공통 캐시 래퍼
