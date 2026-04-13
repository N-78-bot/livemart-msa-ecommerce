# Changelog

All notable changes to LiveMart are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- `docs/runbooks/` — on-call 런북 및 인시던트 대응 가이드

---

## [2.2.0] — 2026-04-13

### Security

- **api-gateway**: X-User-Id / X-User-Role 헤더 위조(Spoofing) 차단 — 공개 경로·JWT 경로·API Key 경로 모두에서 클라이언트 전달 헤더 제거 후 검증된 값으로 교체
- **frontend**: 하드코딩된 GCP VM IP(34.64.189.54) 전면 제거 — `GCP_API_URL` 환경변수로 대체 (next.config.js, page.tsx, route.ts, CSP)
- **payment-service**: TrustedHeaderAuthFilter 추가 — X-User-Id/X-User-Role 기반 SecurityContext 설정
- **SECURITY.md**: Access Token TTL 15분 → 1시간 수정 (application.yml 실제값 반영)

### Fixed

- **k8s/base/ingress.yml**: api-gateway 백엔드 포트 8080 → 8888
- **k8s/base/configmap.yml**: Prometheus 스크레이핑 api-gateway 타겟 포트 8080 → 8888
- **k8s/base/network-policy.yml**: Ingress → api-gateway 허용 포트 8080 → 8888, Prometheus 스크레이핑 포트 목록 실제 서비스 포트로 교체
- **k8s/base/istio-mesh-config.yml**: VirtualService 기본 라우트 api-gateway 포트 8080 → 8888
- **k8s/services/frontend.yml**: NEXT_PUBLIC_API_URL api-gateway 포트 8080 → 8888
- **helm/livemart**: gateway.port 8080 → 8888, ingress 템플릿 하드코딩 포트 제거
- **ci.yml**: inventory-service 빌드 매트릭스 누락 추가, NEXT_PUBLIC_API_URL 포트 수정
- **load-test.yml**: 기본 base_url 8080 → 8888
- **CONTRIBUTING.md**: 커밋 예시 "Stripe 이중 청구" → "Toss 이중 청구"

---

## [2.1.0] — 2026-04-13

### Security

- **README**: 운영 서버 IP 및 테스트 계정 비밀번호 공개 노출 제거
- **docker-compose.yml**: 약한 패스워드 기본값(user123 등) 전면 제거 → `.env.example` 필수값으로 이동 (`:?` 오류 처리)
- **product-service**: GraphiQL 운영 노출 수정 (`enabled: ${GRAPHIQL_ENABLED:false}`)
- **security.yml**: OWASP ZAP DAST 타겟 포트 수정 (8080 → 8888)

### Changed

- **README**: 아키텍처 다이어그램 정확성 개선 — 7개 서비스 → 8개 (notification, analytics 추가)
- **README**: Spring Boot 버전 배지 3.3.6 → 3.4.0 통일 (build.gradle 기준)
- **README**: 서비스 구성 목록에 notification-service, analytics-service 명시 추가

### Removed

- **CI/CD**: `ci-cd.yml` 중복 워크플로우 삭제 (`ci.yml` + `cd.yml`로 역할 분리)
- **README**: 개발 환경 OS 정보 제거 (포트폴리오 불필요 정보)

### Chore

- `.env.example` 전면 보강: Grafana 계정, SMTP, Toss Payments, OpenRouter 등 모든 필수 환경변수 포함
- `master` 브랜치 미병합 커밋 main에 통합 (보안 패치 우선 적용)

---

## [2.0.0] — 2026-03-17

### Added

**보안 & 인프라**
- Istio Service Mesh: `PeerAuthentication` STRICT mTLS 네임스페이스 전체 적용 ([ADR-006](docs/adr/ADR-006-istio-service-mesh.md))
- `AuthorizationPolicy`: payment-service 접근을 order-service + api-gateway로 제한 (SPIFFE ID 기반)
- `DestinationRule`: Circuit Breaker outlierDetection (payment: 3회 오류 → 60초 격리)
- `VirtualService`: 결제 API timeout=10s/retry=3회, 주문 API timeout=15s/retry=2회
- OWASP ZAP DAST 스캔 추가 (CI: PR + 정기 스케줄)
- SonarCloud 통합 (Quality Gate wait + JaCoCo 커버리지 연동)
- Gitleaks v2 전체 Git 히스토리 시크릿 스캔

**AI 서비스**
- `ai-service` (:8090) 배포 — 상품 추천 ML API
- ai-service Kubernetes Deployment / HPA 설정

**재고 시스템**
- 재고 부족 알림 Kafka 이벤트 발행 (`inventory-events` 토픽)
- 자동 발주 트리거 — 임계치 기반 inventory-service 연동

**관찰성**
- ELK Stack 로그 중앙화 (Logstash → Elasticsearch → Kibana)
- Structured JSON 로깅 (`logstash-logback-encoder`)
- Lighthouse CI 통합 — 프론트엔드 성능 점수 자동 측정
- 헬스 대시보드 — Grafana 커스텀 패널 (JVM, Kafka Lag, DB Pool)

**CI/CD**
- GitHub Actions Dependabot 자동 의존성 업데이트 (Gradle/npm/Actions)
- CODEOWNERS 설정 (payment-service, common/, k8s/, helm/ → @parkmin-je)
- PR 템플릿 (`.github/PULL_REQUEST_TEMPLATE.md`)
- 이슈 템플릿 (버그 리포트, 기능 요청)
- `release.yml` — 자동 릴리즈 노트 + GitHub Release 생성

**문서**
- `PERFORMANCE.md` — k6 부하 테스트 정량 결과 (Redis -94%, Spike 0 중복차감 등)
- `ADR-006` — Istio 채택 근거 문서 (Linkerd/Consul Connect 비교, PCI-DSS 근거)
- English Architecture Overview 추가 (README.md — Coupang/Line 글로벌 리뷰어 대상)
- `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`

### Changed

- JaCoCo 서비스 레이어 최소 커버리지: 60% → **70%**
- JaCoCo 컨트롤러 레이어 최소 커버리지 추가: **60%**
- README 전면 개편: 성능 벤치마크 요약 테이블, 영문 섹션 추가

### Removed

- Root 개발 아티팩트 삭제: `TestMySQLConnection.java`, `SETUP_COMPLETE.md`, `TROUBLESHOOTING_FIXES.md`, `SERVICE_STARTUP_GUIDE.md`
- 개발용 스크립트 삭제: `start-*.bat` (6개), `stop-all.bat`, `check-system.bat`, `test-*.ps1`, `test-user-api.sh`
- `secrets.yml` 하드코딩 패스워드 제거 → Kubernetes Secret 참조로 변경

### Security

- `secrets.yml` 하드코딩 패스워드 전면 제거 (Kubernetes Secret 참조 방식으로 교체)
- `secrets.local.yml` `.gitignore` 등록

---

## [1.5.0] — 2025-12-15

### Added

- **ELK Stack** 로그 중앙화 인프라 (`docker-compose-elk.yml`)
- **Lighthouse CI** 프론트엔드 성능 자동화 검사 (`.github/workflows/lighthouse.yml`)
- **GraphQL** 엔드포인트 — product-service `/graphql` (상품 복합 조회)
- **WebSocket** 실시간 재고 현황 알림 — product-service (`/ws/inventory`)
- **S3 연동** — product-service 상품 이미지 업로드

### Changed

- Next.js 14 → **15** 업그레이드
- React Server Components 기본 적용으로 초기 로딩 속도 개선

---

## [1.4.0] — 2025-10-20

### Added

- **Spring Batch** 일별 정산 / 월별 매출 리포트 배치 (order-service)
- **Analytics Service** (:8087) — 실시간 매출 대시보드, A/B 테스트 집계
- **MFA (TOTP/WebAuthn)** — user-service 다단계 인증

### Fixed

- Outbox Poller 재시작 후 이미 발행된 이벤트 중복 발행 버그 수정

---

## [1.3.0] — 2025-08-05

### Added

- **Redisson 분산 락** — 플래시 세일 재고 동시성 제어
- **Saga 보상 트랜잭션** — payment-service 결제 실패 시 order CANCELLED 처리
- **Kafka DLQ** — `*.DLT` Dead Letter Topic (ExponentialBackOff 1s→2s→4s, 3회)
- **k6 부하 테스트** — Smoke / Load / Stress / Spike 시나리오 (`tests/k6/`)

### Performance

- Redis Cache-Aside 적용: 상품 조회 p99 1,240ms → 72ms (-94%)
- Flash Sale 500 VU: 재고 중복 차감 0건 (Redisson 검증)

---

## [1.2.0] — 2025-06-10

### Added

- **Elasticsearch** 상품 검색 (nori 형태소 분석기, Fuzzy, 패싯 집계)
- **gRPC** 서버 스트리밍 — product-service → order-service 상품 조회 ([ADR-003](docs/adr/ADR-003-grpc-product-query.md))
- **OpenTelemetry (OTLP)** 분산 추적 → Zipkin

### Changed

- Zipkin 직접 연동 → OpenTelemetry Collector 경유로 변경

---

## [1.1.0] — 2025-04-22

### Added

- **Transactional Outbox Pattern** — 주문 저장 + Kafka 발행 단일 DB 트랜잭션 ([ADR-002](docs/adr/ADR-002-outbox-pattern.md))
- **Kubernetes HPA** — order-service, payment-service CPU 70% 기준 자동 스케일아웃 (2→10)
- **Prometheus + Grafana** 모니터링 스택
- **AOP 비즈니스 메트릭** — `OrderMetricsAspect` (주문 생성수, 처리시간, 활성 게이지)
- **Spring Cloud Contract** — order↔payment API 계약 테스트

---

## [1.0.0] — 2025-03-01

### Added

- 10개 마이크로서비스 초기 구축:
  - `api-gateway` (Spring Cloud Gateway, Rate Limiting, JWT 검증)
  - `order-service` (주문, Saga Choreography)
  - `payment-service` (Toss Payments 결제)
  - `product-service` (상품 카탈로그)
  - `user-service` (회원, JWT, OAuth2)
  - `inventory-service` (재고 관리)
  - `notification-service` (이메일/알림)
  - `analytics-service` (매출 분석)
  - `eureka-server` (서비스 레지스트리)
  - `config-server` (중앙 설정)
- **Saga Choreography** 분산 트랜잭션 (order → payment → inventory) ([ADR-001](docs/adr/ADR-001-saga-pattern.md))
- **PostgreSQL × 5** DB-per-Service 독립 스키마
- **Redis** Cache-Aside + JWT Blacklist + Rate Limiting
- **Kafka** 이벤트 기반 서비스 간 통신
- **Docker Compose** 로컬 개발 인프라
- **Kubernetes** 배포 매니페스트 + Helm Charts
- **ArchUnit** 레이어 의존성 규칙 CI 검증
- **Testcontainers** PostgreSQL/Kafka 통합 테스트
- **Flyway** DB 마이그레이션
- **Next.js 14** 프론트엔드

---

[Unreleased]: https://github.com/parkmin-je/livemart-msa-ecommerce/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/parkmin-je/livemart-msa-ecommerce/compare/v1.5.0...v2.0.0
[1.5.0]: https://github.com/parkmin-je/livemart-msa-ecommerce/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/parkmin-je/livemart-msa-ecommerce/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/parkmin-je/livemart-msa-ecommerce/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/parkmin-je/livemart-msa-ecommerce/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/parkmin-je/livemart-msa-ecommerce/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/parkmin-je/livemart-msa-ecommerce/releases/tag/v1.0.0
