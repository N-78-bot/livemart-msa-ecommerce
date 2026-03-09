# ADR-003: order-service → product-service 간 gRPC 채택

- **상태**: 채택됨 (Accepted)
- **날짜**: 2026-02-01
- **결정자**: 백엔드 팀

## 배경 (Context)

`order-service`는 주문 생성 시 상품 정보(가격, 재고)를 조회해야 한다.
초기에는 REST/Feign을 사용했으나 다음 성능 문제가 발생:
- 주문 생성 p99: 3.2초 (목표 1초 이하)
- 상품 10개 주문 시 직렬 HTTP 요청 → 누적 지연

## 결정 (Decision)

**상품 조회에 gRPC (HTTP/2 + Protobuf)** 채택. REST/Feign은 write 작업에만 유지.

```protobuf
// product.proto
service ProductGrpcService {
  rpc GetProduct(GetProductRequest) returns (ProductResponse);
  rpc GetProductsByIds(GetProductsByIdsRequest) returns (stream ProductResponse);
  rpc CheckStock(CheckStockRequest) returns (CheckStockResponse);
  rpc DeductStock(DeductStockRequest) returns (DeductStockResponse);
}
```

## 성능 비교 (벤치마크)

| 시나리오 | REST/Feign | gRPC | 개선율 |
|---------|-----------|------|--------|
| 상품 1개 조회 | 45ms | 8ms | **5.6배** |
| 상품 10개 조회 (병렬) | 180ms | 25ms | **7.2배** |
| 직렬화 크기 (상품 DTO) | 1.2KB (JSON) | 380B (Protobuf) | **68% 감소** |

## 채택 이유 (Rationale)

1. **HTTP/2 멀티플렉싱**: 단일 TCP 연결에서 다수 요청 처리 → 연결 오버헤드 제거
2. **Protobuf**: JSON 대비 직렬화 크기 60~70% 감소, 역직렬화 속도 5배 향상
3. **Server Streaming**: 다건 상품 조회 시 스트림으로 순차 응답 처리
4. **강타입 계약**: proto 파일로 서비스 간 인터페이스 명확히 정의

## 트레이드오프

**장점:**
- 상품 조회 성능 5~7배 향상
- 서비스 간 인터페이스 스키마 명확화
- 양방향 스트리밍 지원

**단점:**
- proto 파일 관리 필요 (변경 시 양쪽 재컴파일)
- 디버깅 시 Protobuf 디코딩 필요 (BloomRPC, Postman gRPC)
- REST API에 익숙한 팀원 학습 비용

## 구현 위치

- `product-service/src/main/proto/product.proto`
- `product-service/grpc/ProductGrpcServer.java` (포트 50051)
- `order-service/grpc/ProductGrpcClient.java`
- `order-service/build.gradle`: `grpc-client-spring-boot-starter`
