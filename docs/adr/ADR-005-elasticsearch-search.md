# ADR-005: 상품 검색에 Elasticsearch 채택 (PostgreSQL Full-Text 미채택)

- **상태**: 채택됨 (Accepted)
- **날짜**: 2026-02-15
- **결정자**: 백엔드 팀

## 배경 (Context)

상품 검색 요구사항:
1. 한국어 형태소 분석 (노트북 → 노트, 북 / 스마트폰 → 스마트, 폰)
2. 오타 교정 (fuzzy search): "삼숭" → "삼성"
3. 패싯 검색: 가격대/카테고리/브랜드 필터 + 집계
4. 관련도 스코어링 (제목 가중치 > 설명)
5. 자동완성 (edge-ngram)

PostgreSQL `tsvector`로는 한국어 형태소 분석과 패싯 집계가 불가능.

## 결정 (Decision)

**Elasticsearch 8.x** 채택. PostgreSQL은 트랜잭션 데이터 저장소로만 사용.

### 아키텍처

```
[Write Path]
ProductService → PostgreSQL (원본 저장)
             → Kafka (product-events)
             → ProductEventConsumer → Elasticsearch (인덱싱)

[Read Path - 검색]
Client → AdvancedSearchService → Elasticsearch
       ↳ 필터/집계/스코어링 후 반환

[Read Path - 상세]
Client → ProductService → Redis → PostgreSQL (캐시 미스 시)
```

### 인덱스 설계

```json
{
  "mappings": {
    "properties": {
      "name":        { "type": "text", "analyzer": "nori", "boost": 3.0 },
      "description": { "type": "text", "analyzer": "nori" },
      "price":       { "type": "double" },
      "category":    { "type": "keyword" },
      "brand":       { "type": "keyword" },
      "status":      { "type": "keyword" },
      "name_suggest":{ "type": "completion" }
    }
  }
}
```

## 채택 이유 (Rationale)

| 기능 | PostgreSQL FTS | Elasticsearch |
|------|---------------|---------------|
| 한국어 형태소 | ❌ (플러그인 필요) | ✅ (nori 내장) |
| Fuzzy Search | 제한적 | ✅ |
| 패싯 집계 | 느림 | ✅ (aggs API) |
| 자동완성 | ❌ | ✅ (completion suggester) |
| 수평 확장 | 복잡 | ✅ (샤딩 내장) |
| 운영 복잡도 | 낮음 | 중간 |

## 이중 저장 (Dual Write) 처리

PostgreSQL이 단일 진실 공급원 (Source of Truth):
- ES 인덱스 손상 → `POST /api/products/search/reindex` 로 재구성
- 일관성 지연: Kafka 처리로 최대 수백ms

## 트레이드오프

**장점:**
- 검색 관련성 대폭 향상
- 한국어 검색 품질 (nori 형태소 분석기)
- 복잡한 필터/집계 쿼리 단순화

**단점:**
- 별도 인프라 운영 비용 (메모리 집약적)
- PostgreSQL-ES 데이터 동기화 관리 필요
- 강한 일관성 불가 (검색 결과가 수백ms 지연될 수 있음)

## 구현 위치

- `product-service/document/ProductDocument.java` — ES 인덱스 매핑
- `product-service/search/AdvancedSearchService.java` — 쿼리 빌더
- `product-service/repository/ProductSearchRepository.java` — ES Repository
- `product-service/controller/ProductController.java` — `POST /api/products/search/reindex`
