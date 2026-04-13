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

## Railway 백엔드 배포 (무료 $5/월 크레딧)

### 추천 배포 순서 (최소 데모용)

서비스 간 의존성을 고려해 아래 순서로 배포합니다:

1. **PostgreSQL** — Railway 대시보드 > New > Database > Add PostgreSQL
2. **Redis** — Railway 대시보드 > New > Database > Add Redis
3. **user-service** — 회원가입/로그인 (JWT 발급)
4. **product-service** — 상품 목록 조회
5. **api-gateway** — 외부 진입점, 라우팅 처리

### railway.toml (각 서비스 루트에 생성)

각 마이크로서비스의 루트 디렉토리에 `railway.toml` 파일을 생성합니다.

```toml
[build]
builder = "NIXPACKS"
buildCommand = "../gradlew :서비스명:bootJar -x test"

[deploy]
startCommand = "java -jar build/libs/서비스명-2.0.0.jar"
healthcheckPath = "/actuator/health"
healthcheckTimeout = 300
```

예시 (user-service):
```toml
[build]
builder = "NIXPACKS"
buildCommand = "../gradlew :user-service:bootJar -x test"

[deploy]
startCommand = "java -jar build/libs/user-service-2.0.0.jar"
healthcheckPath = "/actuator/health"
healthcheckTimeout = 300
```

### 환경변수 (Railway 대시보드 > 서비스 > Variables)

```
SPRING_DATASOURCE_URL=${{Postgres.DATABASE_URL}}
SPRING_REDIS_HOST=${{Redis.REDIS_HOST}}
SPRING_REDIS_PORT=${{Redis.REDIS_PORT}}
JWT_SECRET=your-secret-최소32자이상으로설정하세요
SPRING_PROFILES_ACTIVE=prod
EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://eureka-service.railway.internal:8761/eureka/
```

---

## 빠른 데모 실행 (로컬)

### 인프라만 Docker로 띄우고 나머지는 로컬 실행

```bash
# 1. 인프라 (PostgreSQL, Redis, Kafka 등) Docker로 실행
docker-compose -f docker-compose.infra.yml up -d

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
