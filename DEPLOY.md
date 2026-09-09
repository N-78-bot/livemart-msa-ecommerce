# 배포 가이드

## Vercel 프론트엔드 배포 (무료)

### 1단계: Vercel 계정 & 연결

1. [vercel.com](https://vercel.com) 에서 GitHub 계정으로 가입/로그인
2. 대시보드에서 **"Add New Project"** 클릭
3. GitHub 저장소 목록에서 `livemart-msa-ecommerce` 선택 후 **"Import"** 클릭
4. 루트에 `vercel.json`이 있으므로 Vercel이 자동으로 `frontend/` 디렉토리를 감지함
   - Framework Preset: **Next.js** (자동 감지)
   - Root Directory: **frontend** (자동 감지)
5. **"Deploy"** 클릭 — 첫 배포 시작

> **참고:** 환경변수를 나중에 추가해도 재배포로 반영 가능합니다. 백엔드 없이 UI만 먼저 배포해도 됩니다.

---

### 2단계: 환경변수 설정

Vercel 대시보드 > 프로젝트 선택 > **Settings > Environment Variables** 에서 다음 항목 추가:

```
API_GATEWAY_URL=https://your-backend-url.com
NOTIFICATION_SERVICE_URL=https://your-notification-url.com
NEXT_PUBLIC_API_URL=https://your-backend-url.com
```

| 변수명 | 설명 | 예시 |
|--------|------|------|
| `API_GATEWAY_URL` | Spring API Gateway URL (서버 사이드 프록시용) | `https://api.livemart.com` |
| `NOTIFICATION_SERVICE_URL` | SSE 알림 서비스 직접 연결 URL | `https://notif.livemart.com` |
| `NEXT_PUBLIC_API_URL` | 클라이언트 사이드에서 접근하는 API URL | `https://api.livemart.com` |

> **보안 주의:** JWT_SECRET 등 민감한 값은 절대 `vercel.json`에 넣지 마세요. 반드시 Vercel 대시보드 환경변수로만 관리합니다.

환경변수 추가 후 **"Redeploy"** 를 눌러 재배포합니다.

---

### 3단계: 배포 확인

- 배포 완료 후 도메인 자동 발급: `https://livemart-xxx.vercel.app`
- **자동 배포:** `main` 브랜치에 push 하면 자동으로 재배포됩니다.
- **미리보기 배포:** PR을 열면 preview URL이 자동 생성됩니다.

---

## Render 백엔드 배포 (무료 플랜)

실제 운영 중인 백엔드는 Railway가 아니라 **Render**입니다. 레포 루트의 `render.yaml`이
[Render Blueprint](https://render.com/docs/blueprint-spec) 형식으로 9개 서비스를 정의합니다:
`api-gateway`, `user-service`, `product-service`, `order-service`, `payment-service`,
`ai-service`, `inventory-service`, `notification-service`, `analytics-service`
(각 서비스는 `Dockerfile`로 빌드되며 `dockerContext: .` — 레포 루트 기준으로 빌드됩니다).

`eureka-server`는 Render에 배포하지 않습니다. `render` 프로파일에서는 모든 서비스가
Eureka 없이 서로의 URL을 환경변수(`USER_SERVICE_URL`, `PRODUCT_SERVICE_URL` 등)로
직접 라우팅합니다 — Render 서비스 간에는 Eureka 같은 내부 서비스 디스커버리가 없기 때문입니다.

### 1단계: Blueprint로 서비스 생성

1. Render 대시보드 > **New > Blueprint** > 이 저장소 선택
2. `render.yaml`을 인식해 9개 서비스가 한 번에 생성됨 (모두 `plan: free`)
3. 각 서비스는 `healthCheckPath: /actuator/health`로 헬스체크됨

> Render 무료 플랜은 서비스당 512MB RAM, 15분 무활동 시 슬립 — 콜드스타트 시 첫 요청이
> 느릴 수 있습니다. 각 서비스 Dockerfile에는 `-XX:MaxRAMPercentage=75.0` 등 512MB 환경에
> 맞춘 JVM 힙 튜닝이 되어 있습니다.

### 2단계: 외부 관리형 DB/Redis 준비 (Render는 무료 Postgres/Redis를 제공하지 않음)

`render.yaml`의 `DATABASE_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_URL` 등은 전부
`sync: false`로 선언되어 있어 **Render가 자동 생성하지 않고, 직접 값을 넣어야 합니다.**

- **Postgres**: [Neon](https://neon.tech) 무료 플랜에서 서비스별로 DB를 만들어
  `DATABASE_URL`/`DB_USERNAME`/`DB_PASSWORD`로 연결 (user/product/order/payment/
  inventory/analytics-service 각각 별도 DB 권장)
- **Redis**: [Upstash](https://upstash.com) 무료 플랜에서 만들어 `REDIS_URL`로 연결
  (api-gateway/user/product/order/ai/notification-service가 사용)

### 3단계: 서비스별 환경변수 (Render 대시보드 > 서비스 > Environment)

`render.yaml`에 `sync: false`로 선언된 항목은 대시보드에서 직접 값을 입력해야 합니다.
서비스별로 필요한 값은 `render.yaml`을 참고하되, 특히 아래는 빠뜨리면 안 됩니다:

| 서비스 | 필수 env | 비고 |
|--------|----------|------|
| api-gateway | `JWT_SECRET` | user/order-service와 **동일한 값**이어야 함 (JWT 서명 검증) |
| user-service | `JWT_SECRET`, `SMTP_USERNAME`/`PASSWORD`, OAuth2 클라이언트 ID/Secret | 소셜 로그인 미사용 시 OAuth2 값은 비워도 됨 |
| payment-service | `TOSS_SECRET_KEY` | `TOSS_CLIENT_KEY`는 테스트 키가 이미 커밋되어 있음 |
| notification-service | `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail 앱 비밀번호 사용 권장 |

> **알려진 제약:** Kafka와 Elasticsearch는 Render에 관리형 서비스가 없어 현재 프로덕션에서는
> 연결되지 않습니다 (기본값인 `localhost:9092`/`localhost:9200`으로 접속을 시도하다 실패).
> Saga/Outbox 이벤트 흐름과 상품 검색은 로컬 `docker-compose` 환경(Kafka/ES 포함)에서
> 시연하는 것을 권장합니다. Render 프로파일에서 이 의존성을 정식으로 끄거나 외부 관리형
> Kafka/ES(Upstash Kafka, Bonsai 등)로 교체하는 작업은 별도로 진행 예정입니다.

### 4단계: 배포 확인

`https://<서비스명>.onrender.com/actuator/health`가 `{"status":"UP"}`을 반환하는지
서비스별로 확인합니다. api-gateway가 정상이어야 프론트엔드(Vercel)에서 API 호출이 됩니다.

---

## 빠른 데모 실행 (로컬)

### 인프라만 Docker로 띄우고 나머지는 로컬 실행

```bash
# 1. 인프라 (PostgreSQL, Redis, Kafka 등) Docker로 실행
docker-compose -f docker-compose-infra.yml up -d

# 2. 서비스 순서대로 실행 (각각 새 터미널에서)
./gradlew :eureka-server:bootRun
./gradlew :api-gateway:bootRun
./gradlew :user-service:bootRun -Dspring.profiles.active=local
./gradlew :product-service:bootRun -Dspring.profiles.active=local

# 3. 프론트엔드
cd frontend && npm run dev
```

### 접속 URL

| 서비스 | URL |
|--------|-----|
| 프론트엔드 | http://localhost:3000 |
| API Gateway | http://localhost:8888 |
| Eureka Dashboard | http://localhost:8761 |

---

## 테스트 계정

로컬 실행 후 `/api/users/signup` 엔드포인트로 직접 계정을 생성하거나,
Flyway 마이그레이션 시드 데이터를 통해 초기 계정이 생성됩니다.
데모 계정 정보가 필요하면 [Issues](https://github.com/parkmin-je/livemart-msa-ecommerce/issues)로 문의해주세요.
