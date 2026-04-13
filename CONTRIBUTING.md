# Contributing to LiveMart

LiveMart는 MSA 기반 이커머스 플랫폼입니다. 기여에 앞서 이 가이드를 숙독해주세요.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Branch Strategy](#branch-strategy)
- [Commit Convention](#commit-convention)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Testing Requirements](#testing-requirements)
- [Local Development Setup](#local-development-setup)

---

## Code of Conduct

이 프로젝트는 [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md)를 따릅니다. 참여 전에 반드시 읽어주세요.

---

## Getting Started

1. **Fork** this repository
2. **Clone** your fork: `git clone https://github.com/<your-username>/livemart-msa-ecommerce.git`
3. **Set upstream**: `git remote add upstream https://github.com/parkmin-je/livemart-msa-ecommerce.git`
4. Follow the [Local Development Setup](#local-development-setup) guide

---

## Branch Strategy

```
main          ─── production-ready (protected, requires PR + review)
develop       ─── integration branch
feat/*        ─── new features         (e.g., feat/order-cancellation)
fix/*         ─── bug fixes            (e.g., fix/payment-duplicate-charge)
refactor/*    ─── code refactoring
chore/*       ─── infra / tooling
docs/*        ─── documentation only
release/*     ─── release preparation  (e.g., release/2.1.0)
hotfix/*      ─── urgent production fix (branched from main)
```

### Rules

- `main` 브랜치에 직접 push 금지 — 반드시 PR을 통해 머지
- 모든 feature 브랜치는 `develop`에서 분기
- hotfix 브랜치만 `main`에서 직접 분기 허용
- 브랜치 이름은 소문자 + 하이픈 사용 (예: `feat/wishlist-api`)

---

## Commit Convention

### Format

```
type(scope): 설명 (50자 이내, 한국어)

- 상세 설명 (선택사항, 72자 줄바꿈)
```

### Types

| Type | 설명 |
|------|------|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변경 없는 코드 개선 |
| `perf` | 성능 개선 |
| `test` | 테스트 추가/수정 |
| `docs` | 문서 수정 |
| `chore` | 빌드/툴링/설정 변경 |
| `style` | 코드 포맷 (로직 변경 없음) |
| `security` | 보안 관련 수정 |
| `ci` | CI/CD 설정 변경 |

### Scopes

`order` `payment` `product` `user` `gateway` `notification` `analytics` `inventory` `kafka` `redis` `k8s` `frontend` `common`

### Examples

```bash
feat(order): 쿠폰 적용 API 추가
fix(payment): Toss 이중 청구 방지 멱등성 키 수정
perf(product): Redis 캐시 TTL 계층화로 DB 부하 96% 절감
security(user): httpOnly 쿠키 적용 및 SameSite=Strict 설정
```

---

## Pull Request Process

### Before Submitting

- [ ] `./gradlew test` 로컬 통과 확인
- [ ] `./gradlew jacocoTestCoverageVerification` 커버리지 게이트 통과
- [ ] 관련 테스트 추가 (서비스 레이어 ≥ 70%)
- [ ] `application.yml` 에 민감 정보 미포함 확인
- [ ] CHANGELOG.md 업데이트 (기능 변경 시)

### PR Title Format

```
[type] 한글 설명 (50자 이내)
```

예: `[feat] 재고 알림 Kafka 이벤트 발행 구현`

### Review Process

1. PR 생성 → CI 자동 실행 (test + security scan + coverage)
2. 코드 리뷰 최소 1명 승인 필요
3. `payment-service`, `common/`, `k8s/` 변경 시 CODEOWNER 리뷰 필수
4. CI 전체 통과 후 머지

---

## Coding Standards

### Java

- **Java 21** + Spring Boot 3.4.x
- **Project Loom Virtual Threads** — `spring.threads.virtual.enabled=true`
- **Layer 의존 방향**: `controller → service → repository → domain`
  - Domain 객체는 Spring 의존성 없음 (ArchUnit CI 검증)
- **Lombok** 활용 (`@RequiredArgsConstructor`, `@Builder`, `@Slf4j`)
- **예외 처리**: RFC 7807 Problem Details (`ErrorResponse` in `common/`)
- **로깅**: `@Slf4j` + MDC (traceId, spanId, userId 필수 포함)
- **트랜잭션**: `@Transactional(readOnly = true)` 읽기 메서드, 쓰기는 `@Transactional`

### API Design

- RESTful 설계 원칙 준수
- 응답: `ResponseEntity<T>` + HTTP 상태 코드 명시
- 페이지네이션: `Pageable` + `Page<T>` 리턴
- 검증: `@Valid` + `javax.validation` 어노테이션

### Database

- **Flyway** 마이그레이션 필수 (`src/main/resources/db/migration/V{n}__{description}.sql`)
- JPA N+1 문제 주의 — `@EntityGraph` 또는 fetch join 사용
- 인덱스 추가 시 마이그레이션에 포함

### Frontend (Next.js 15)

- TypeScript strict mode
- 컴포넌트: `function` 선언 방식 (화살표 함수 지양)
- Server Component 우선, Client Component 필요 시 `'use client'` 명시
- 스타일: Tailwind CSS + CSS Modules (글로벌 스타일은 최소화)

---

## Testing Requirements

### 필수 테스트

| 레이어 | 최소 커버리지 | 도구 |
|--------|-------------|------|
| Service | 70% | JUnit 5 + Mockito |
| Controller | 60% | `@WebMvcTest` |
| Repository | — | Testcontainers (PostgreSQL) |

### 테스트 종류

```bash
# 단위 테스트
./gradlew :order-service:test

# 통합 테스트 (Testcontainers 필요)
./gradlew :order-service:test -Dspring.profiles.active=integration

# 아키텍처 테스트
./gradlew :order-service:architecture

# 계약 테스트
./gradlew :payment-service:contractTest

# 커버리지 검증
./gradlew jacocoTestCoverageVerification
```

### 테스트 작성 원칙

- 테스트명: `should_[결과]_when_[조건]` 형식 (예: `should_throw_exception_when_stock_insufficient`)
- Mockito mock은 외부 의존성에만 사용 (DB는 Testcontainers 사용)
- `@DisplayName` 으로 한국어 설명 추가

---

## Local Development Setup

### Prerequisites

- JDK 21 (OpenJDK / Temurin)
- Docker Desktop + Kubernetes enabled
- Node.js 20+
- Gradle 8.5 (gradlew 사용 권장)

### Infrastructure Setup

```bash
# PostgreSQL, Redis, Kafka, Elasticsearch 기동
docker-compose -f docker-compose-infra.yml up -d

# 컨테이너 상태 확인
docker-compose -f docker-compose-infra.yml ps
```

### Backend Build & Run

```bash
# 전체 빌드 (테스트 제외)
./gradlew build -x test --parallel

# 개별 서비스 실행 (순서 중요)
./gradlew :config-server:bootRun &
./gradlew :eureka-server:bootRun &
./gradlew :api-gateway:bootRun &
./gradlew :order-service:bootRun &
```

### Frontend Setup

```bash
cd frontend
npm install
npm run dev   # http://localhost:3000
```

### Environment Variables

각 서비스의 `src/main/resources/application.yml`에서 환경변수를 참조합니다.
민감 정보는 `.env.local` 또는 Kubernetes Secrets를 사용합니다.

```bash
# 예시: order-service/.env.local (gitignore됨)
SPRING_DATASOURCE_PASSWORD=your_password
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

### IDE Setup (IntelliJ IDEA)

1. `File > Open` → 프로젝트 루트 선택
2. Gradle 동기화 완료 대기
3. `.editorconfig` 자동 적용 확인
4. `Annotaton Processors > Enable annotation processing` 활성화 (Lombok)

---

## Questions?

이슈 트래커를 통해 질문하거나 [GitHub Discussions](https://github.com/parkmin-je/livemart-msa-ecommerce/discussions)를 이용해주세요.
