# Railway 클라우드 배포 가이드

## 전체 아키텍처

```
[Vercel Frontend]
       ↓ API_GATEWAY_URL
[Railway API Gateway] ─── Eureka lb:// 라우팅
       ↓
[Railway Eureka Server]
       ↓
┌──────────────────────────────────────────┐
│  user-service  │  product-service        │
│  order-service │  payment-service        │
│  ai-service    │  inventory-service      │
└──────────────────────────────────────────┘
       ↓                    ↓
[Railway PostgreSQL]   [Railway Redis]
  (서비스별 각 1개)      (공유 1개)
```

---

## STEP 1: Railway 계정 및 프로젝트 생성

1. [railway.app](https://railway.app) → GitHub 로그인
2. **New Project** → **Empty Project** 클릭
3. 프로젝트 이름: `livemart`

---

## STEP 2: 인프라 서비스 추가

### Redis (공유)
1. 프로젝트 내 **+ Add Service** → **Database** → **Add Redis**
2. 생성 후 Redis 서비스 클릭 → **Variables** 탭에서 `REDIS_URL`, `REDIS_HOST`, `REDIS_PORT` 확인

### PostgreSQL — 서비스별 각 1개 생성
각 서비스마다 별도 PostgreSQL 인스턴스를 추가합니다.

| 서비스 | PostgreSQL 이름 |
|--------|----------------|
| user-service | `user-db` |
| product-service | `product-db` |
| order-service | `order-db` |
| payment-service | `payment-db` |
| inventory-service | `inventory-db` |

**PostgreSQL 추가 방법:**
1. **+ Add Service** → **Database** → **Add PostgreSQL**
2. 서비스 이름 변경: `user-db`, `product-db` 등

---

## STEP 3: 서비스 배포 (순서 중요!)

> Railway에서 GitHub 레포를 연결해 각 서비스를 배포합니다.
> 각 서비스는 **Root Directory**를 해당 서비스 폴더로 지정합니다.

### 3-1. Eureka Server (가장 먼저!)
1. **+ Add Service** → **GitHub Repo** → `livemart-msa-ecommerce` 선택
2. **Root Directory**: `eureka-server`
3. 서비스 이름: `eureka-server`
4. 배포 확인 후 다음 서비스 진행

### 3-2. API Gateway
1. **+ Add Service** → **GitHub Repo** (같은 레포)
2. **Root Directory**: `api-gateway`
3. 서비스 이름: `api-gateway`
4. **Variables** 탭에서 아래 환경변수 설정:

```
SPRING_PROFILES_ACTIVE=railway
EUREKA_URL=http://eureka-server.railway.internal:8761/eureka/
REDIS_HOST=${{Redis.REDIS_HOST}}
REDIS_PORT=${{Redis.REDIS_PORT}}
CORS_ALLOWED_ORIGINS=https://livemart-parkmin-jes-projects.vercel.app
```

5. **Settings** → **Networking** → **Generate Domain** (공개 URL 발급)
   - 발급된 URL 메모: `https://api-gateway-xxx.up.railway.app`

### 3-3. User Service
**Root Directory**: `user-service`
**Variables:**

```
SPRING_PROFILES_ACTIVE=railway
EUREKA_URL=http://eureka-server.railway.internal:8761/eureka/
PGHOST=${{user-db.PGHOST}}
PGPORT=${{user-db.PGPORT}}
PGDATABASE=${{user-db.PGDATABASE}}
PGUSER=${{user-db.PGUSER}}
PGPASSWORD=${{user-db.PGPASSWORD}}
REDIS_HOST=${{Redis.REDIS_HOST}}
REDIS_PORT=${{Redis.REDIS_PORT}}
JWT_SECRET=<openssl rand -base64 64 으로 생성>
SMTP_USERNAME=<네이버 이메일>
SMTP_PASSWORD=<네이버 SMTP 비밀번호>
KAKAO_CLIENT_ID=<카카오 앱 키>
KAKAO_CLIENT_SECRET=<카카오 시크릿>
NAVER_CLIENT_ID=<네이버 클라이언트 ID>
NAVER_CLIENT_SECRET=<네이버 시크릿>
GOOGLE_CLIENT_ID=<구글 클라이언트 ID>
GOOGLE_CLIENT_SECRET=<구글 시크릿>
OAUTH2_REDIRECT_URI=https://livemart-parkmin-jes-projects.vercel.app/auth/oauth2/callback
OAUTH2_BASE_URL=https://api-gateway-xxx.up.railway.app
```

### 3-4. Product Service
**Root Directory**: `product-service`
**Variables:**

```
SPRING_PROFILES_ACTIVE=railway
EUREKA_URL=http://eureka-server.railway.internal:8761/eureka/
PGHOST=${{product-db.PGHOST}}
PGPORT=${{product-db.PGPORT}}
PGDATABASE=${{product-db.PGDATABASE}}
PGUSER=${{product-db.PGUSER}}
PGPASSWORD=${{product-db.PGPASSWORD}}
REDIS_HOST=${{Redis.REDIS_HOST}}
REDIS_PORT=${{Redis.REDIS_PORT}}
```

### 3-5. Order Service
**Root Directory**: `order-service`
**Variables:**

```
SPRING_PROFILES_ACTIVE=railway
EUREKA_URL=http://eureka-server.railway.internal:8761/eureka/
PGHOST=${{order-db.PGHOST}}
PGPORT=${{order-db.PGPORT}}
PGDATABASE=${{order-db.PGDATABASE}}
PGUSER=${{order-db.PGUSER}}
PGPASSWORD=${{order-db.PGPASSWORD}}
REDIS_HOST=${{Redis.REDIS_HOST}}
REDIS_PORT=${{Redis.REDIS_PORT}}
JWT_SECRET=<user-service와 동일한 값>
```

### 3-6. Payment Service
**Root Directory**: `payment-service`
**Variables:**

```
SPRING_PROFILES_ACTIVE=railway
EUREKA_URL=http://eureka-server.railway.internal:8761/eureka/
PGHOST=${{payment-db.PGHOST}}
PGPORT=${{payment-db.PGPORT}}
PGDATABASE=${{payment-db.PGDATABASE}}
PGUSER=${{payment-db.PGUSER}}
PGPASSWORD=${{payment-db.PGPASSWORD}}
TOSS_CLIENT_KEY=test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq
TOSS_SECRET_KEY=<토스 시크릿 키>
DB_PASSWORD=${{payment-db.PGPASSWORD}}
```

### 3-7. AI Service
**Root Directory**: `ai-service`
**Variables:**

```
SPRING_PROFILES_ACTIVE=railway
EUREKA_URL=http://eureka-server.railway.internal:8761/eureka/
REDIS_HOST=${{Redis.REDIS_HOST}}
REDIS_PORT=${{Redis.REDIS_PORT}}
OPENAI_API_KEY=<OpenAI API 키 — 없으면 비워도 fallback 동작>
```

### 3-8. Inventory Service
**Root Directory**: `inventory-service`
**Variables:**

```
SPRING_PROFILES_ACTIVE=railway
EUREKA_URL=http://eureka-server.railway.internal:8761/eureka/
PGHOST=${{inventory-db.PGHOST}}
PGPORT=${{inventory-db.PGPORT}}
PGDATABASE=${{inventory-db.PGDATABASE}}
PGUSER=${{inventory-db.PGUSER}}
PGPASSWORD=${{inventory-db.PGPASSWORD}}
REDIS_HOST=${{Redis.REDIS_HOST}}
REDIS_PORT=${{Redis.REDIS_PORT}}
DB_PASSWORD=${{inventory-db.PGPASSWORD}}
```

---

## STEP 4: OAuth2 소셜 로그인 Redirect URI 업데이트

API Gateway Railway URL이 발급되면 각 소셜 로그인 앱에 등록:

| 제공자 | 등록할 Redirect URI |
|--------|-------------------|
| 카카오 | `https://api-gateway-xxx.up.railway.app/login/oauth2/code/kakao` |
| 네이버 | `https://api-gateway-xxx.up.railway.app/login/oauth2/code/naver` |
| 구글 | `https://api-gateway-xxx.up.railway.app/login/oauth2/code/google` |

> `api-gateway-xxx.up.railway.app`을 실제 발급된 URL로 교체

---

## STEP 5: Vercel 환경변수 업데이트

Railway API Gateway URL 발급 후:

```bash
cd frontend

# 기존 환경변수 삭제 후 재설정
npx vercel env rm API_GATEWAY_URL production --yes
npx vercel env rm NEXT_PUBLIC_API_URL production --yes

echo "https://api-gateway-xxx.up.railway.app" | npx vercel env add API_GATEWAY_URL production
echo "https://api-gateway-xxx.up.railway.app" | npx vercel env add NEXT_PUBLIC_API_URL production

# 재배포
npx vercel --prod --yes
```

---

## STEP 6: 데이터 시드 (초기 상품 데이터)

product-service 배포 완료 후, Railway 대시보드에서 product-db에 접속해 초기 데이터 삽입:

**Railway Dashboard** → `product-db` → **Data** 탭 → SQL Editor에서 실행
또는 product-service가 실행되면 Flyway 마이그레이션이 자동 실행됩니다.

---

## 배포 완료 확인

| 확인 항목 | URL |
|-----------|-----|
| Eureka 대시보드 | Railway 내부 (외부 노출 안 함) |
| API Gateway 헬스체크 | `https://api-gateway-xxx.up.railway.app/actuator/health` |
| User Service | `https://api-gateway-xxx.up.railway.app/api/users/health` |
| Product Service | `https://api-gateway-xxx.up.railway.app/api/products?size=5` |
| Vercel 프론트엔드 | `https://livemart-parkmin-jes-projects.vercel.app` |

---

## 비용 예상 (Railway Hobby Plan $5/월)

| 서비스 | 예상 비용 |
|--------|----------|
| 5× PostgreSQL | ~$2.50 |
| 1× Redis | ~$0.50 |
| 8× 앱 서비스 | ~$1.50 |
| **합계** | **~$4.50/월** |

> Hobby Plan 크레딧 $5/월로 커버 가능

---

## 트러블슈팅

**서비스가 Eureka에 등록 안 됨:**
- Eureka Server가 먼저 healthy 상태인지 확인
- 서비스 재시작 (Railway 대시보드 → Restart)

**DB 연결 실패:**
- `${{service-name.PGHOST}}` 참조 변수가 올바른지 확인
- PostgreSQL 서비스 이름과 참조 이름이 일치하는지 확인

**CORS 오류:**
- API Gateway의 `CORS_ALLOWED_ORIGINS` 환경변수에 Vercel URL 포함 확인
