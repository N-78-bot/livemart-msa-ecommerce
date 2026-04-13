# Security Policy

## Supported Versions

현재 보안 패치가 제공되는 버전:

| Version | Supported          |
|---------|--------------------|
| 2.x     | ✅ Active support  |
| 1.x     | ❌ End of life     |

---

## Reporting a Vulnerability

**공개 이슈로 보안 취약점을 제보하지 마세요.** 취약점이 공개되기 전에 악용될 수 있습니다.

### 제보 방법

1. **GitHub Security Advisories** (권장): [보안 취약점 비공개 제보](https://github.com/parkmin-je/livemart-msa-ecommerce/security/advisories/new)
2. **이메일**: `security@livemart.dev` (PGP 키 사용 권장)

### 제보 시 포함 사항

- 취약점 유형 (예: SQL Injection, XSS, IDOR)
- 영향받는 서비스/엔드포인트
- 재현 단계 (PoC 코드 포함 시 우선 처리)
- 예상 영향 범위

---

## Response Timeline

| 단계 | 목표 기간 |
|------|---------|
| 초기 확인 (접수) | 48시간 이내 |
| 심각도 평가 | 5 영업일 이내 |
| 패치 개발 | Critical: 7일 / High: 14일 / Medium: 30일 |
| 패치 배포 및 공개 | 패치 완료 후 즉시 |

보안 제보자에게는 수정 완료 전까지 비공개 처리 협조를 요청합니다.

---

## Security Architecture

### Authentication & Authorization

- **JWT httpOnly Cookie**: XSS를 통한 토큰 탈취 방지
- **SameSite=Strict**: CSRF 방지
- **Access Token TTL**: 1시간 / Refresh Token TTL: 7일
- **JWT Blacklist**: Redis 기반 로그아웃 토큰 무효화
- **OAuth2**: Google / Kakao / Naver (PKCE 적용)
- **MFA**: TOTP (Google Authenticator) + WebAuthn (패스키)

### Transport Security

- **TLS 1.3**: 클라이언트-서버 통신 전구간
- **Istio mTLS STRICT**: 서비스 간(East-West) 통신 자동 암호화
- **HSTS**: `max-age=31536000; includeSubDomains`

### Secret Management

- **Kubernetes Secrets**: 프로덕션 환경 시크릿 관리
- **환경변수 분리**: `application.yml`에 하드코딩 금지
- **`.gitignore`**: `*.local.yml`, `*.env`, `secrets.yml` 패턴 적용

### Dependency Security

| 도구 | 주기 | 설명 |
|------|------|------|
| Trivy | PR/매일 02:00 UTC | 컨테이너 + 파일시스템 취약점 스캔 (CRITICAL/HIGH) |
| Gitleaks | 모든 push | 하드코딩 시크릿 탐지 (전체 히스토리) |
| CodeQL | PR + 매일 | Java SAST (security-extended 룰셋) |
| OWASP ZAP | PR + 매일 02:00 UTC | DAST API Gateway 대상 (포트 8888) |
| Dependabot | 매주 월요일 | Gradle/npm/Actions 의존성 자동 업데이트 |
| npm audit | PR | 프론트엔드 의존성 취약점 감사 |

보안 스캔 결과는 GitHub Security 탭 → SARIF 형식으로 통합됩니다.

### API Security

- **Rate Limiting**: Redis Token Bucket (100 RPS 기본 / 주문·결제 20 RPS)
- **API Key 인증**: 외부 연동 서비스용
- **X-User-* 헤더 위조 방지**: API Gateway에서 클라이언트 요청의 X-User-Id/Role 헤더 제거 후 JWT 검증 완료된 값으로 교체
- **Circuit Breaker**: Resilience4j (Istio DestinationRule 이중 보호)
- **Input Validation**: Bean Validation (`@Valid`) + Hibernate Validator

### Payment Security

- **서버 금액 검증**: 클라이언트 전달 금액 무시, order-service에서 실제 금액 재조회 (CVSS 9.3 취약점 수정)
- **Idempotency Key**: 결제 중복 청구 방지
- **Toss Payments Webhook 검증**: `Authorization` Basic 헤더 서명 검증
- **결제 정보 비저장**: 카드 데이터 직접 저장 금지, Toss 위임 처리

---

## Security Best Practices for Contributors

1. **시크릿 커밋 금지**: 비밀번호, API 키, 인증서를 코드에 직접 포함하지 마세요
2. **SQL Injection 방지**: JPA/JPQL 파라미터 바인딩 사용, 문자열 연결 금지
3. **XSS 방지**: React의 기본 이스케이프 활용, `dangerouslySetInnerHTML` 사용 금지
4. **의존성 최신화**: Dependabot PR 신속 검토 및 머지
5. **로깅 주의**: 비밀번호, 토큰, PII 데이터 로그 출력 금지

---

## OWASP Top 10 대응 현황 (2021 기준)

| # | 취약점 | 대응 방법 | 상태 |
|---|-------|---------|------|
| A01 | Broken Access Control | Spring Security `@PreAuthorize`, Istio AuthorizationPolicy (service-to-service), RBAC (K8s) | ✅ |
| A02 | Cryptographic Failures | JWT HS512, TLS 1.3, Istio mTLS STRICT, httpOnly/Secure Cookie, 민감정보 로그 제외 | ✅ |
| A03 | Injection | JPA 파라미터 바인딩 (PreparedStatement), Spring Security CSRF 토큰, Bean Validation 입력 검증 | ✅ |
| A04 | Insecure Design | Transactional Outbox (이중 송금 방지), 결제 서비스 격리, ADR 기반 보안 결정 기록 | ✅ |
| A05 | Security Misconfiguration | Trivy 컨테이너 스캔 (CI), Gitleaks (하드코딩 시크릿 탐지), Helm secret 템플릿, 비-root Docker 실행 | ✅ |
| A06 | Vulnerable Components | Dependabot (주간 자동 PR), `npm audit` (CI), Trivy SCA, CodeQL 분석 | ✅ |
| A07 | Identification & Authentication Failures | OAuth2 PKCE / client_secret_post, JWT Blacklist (Redis), Access Token 1시간 TTL, Refresh Token Rotation | ✅ |
| A08 | Software & Data Integrity Failures | Docker 이미지 서명 (GHCR), Helm provenance, Kubernetes NetworkPolicy (East-West 차단) | ✅ |
| A09 | Security Logging & Monitoring Failures | ELK Stack 중앙 로그, Zipkin 분산 추적, Grafana 알람, Slack 실시간 알림 | ✅ |
| A10 | Server-Side Request Forgery (SSRF) | SSRF 대상 URL 화이트리스트 (Feign Client), Istio Egress Gateway (외부 호출 제한), SecurityContext URL 검증 | ✅ |

### 정기 검토 일정

- **분기별**: OWASP ZAP DAST 전체 스캔 결과 검토
- **반기별**: 외부 침투 테스트 (계획 중)
- **연간**: OWASP Top 10 개정 사항 반영

---

## Bug Bounty

현재 공식 Bug Bounty 프로그램은 운영하지 않으나, 중요 취약점 제보자에게는
CHANGELOG에 크레딧을 제공합니다.
