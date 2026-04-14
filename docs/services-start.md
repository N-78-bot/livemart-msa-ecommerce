# LiveMart 서비스 시작 가이드

## ✅ 문제 해결 완료!

다음 두 가지 문제를 해결했습니다:

### 1. API Gateway Eureka 등록 실패 ✅
- **수정**: `ApiGatewayApplication.java`에 `@EnableDiscoveryClient` 추가
- **파일**: `C:\project\livemart\api-gateway\src\main\java\com\livemart\apigateway\ApiGatewayApplication.java`

### 2. 명령줄 길이 오류 ✅
- **수정**: 모든 Run Configuration에 `ARGS_FILE` 옵션 추가
- **영향**: ProductService, UserService, PaymentService, NotificationService, ApiGateway, EurekaServer

---

## 🚀 서비스 시작 방법

### 1단계: 인프라 확인

Docker 컨테이너가 실행 중인지 확인:

```bash
docker ps
```

**실행 중이어야 할 컨테이너:**
- ✅ livemart-redis (6379)
- ✅ livemart-kafka (9092)
- ✅ livemart-zookeeper (2181)
- ✅ livemart-elasticsearch (9200)
- ✅ livemart-postgres-payment (5432)

**실행되지 않은 경우:**
```bash
cd C:\project\livemart
docker-compose up -d redis kafka zookeeper elasticsearch postgres-payment
```

---

### 2단계: MySQL 데이터베이스 확인

IntelliJ Database Tools에서 확인:
1. 우측 Database 탭 → `+` → MySQL
2. Host: `localhost`, Port: `3306`, User: `root`, Password: `root`
3. 데이터베이스 목록 확인

**필요한 데이터베이스:**
- `userdb`
- `productdb`
- `orderdb`

**없는 경우 생성:**

IntelliJ Database Tools:
1. MySQL 연결 우클릭 → New → Database
2. 이름: `userdb`, Charset: `utf8mb4`, Collation: `utf8mb4_unicode_ci`
3. `productdb`, `orderdb` 동일하게 생성

또는 SQL 실행:
```sql
CREATE DATABASE IF NOT EXISTS userdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS productdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS orderdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

### 3단계: 서비스 시작 (IntelliJ)

**중요: 반드시 순서대로 시작!**

#### 1️⃣ Eureka Server (먼저 시작)
```
Run → Run 'EurekaServerApplication'
또는 Shift + F10
```

**대기**: 콘솔에서 다음 메시지 확인
```
Started EurekaServerApplication in X seconds
```

**확인**: http://localhost:8761 접속 가능

---

#### 2️⃣ 다른 서비스들 (순서 무관)

각 서비스를 순차적으로 시작:

1. **User Service** (8081)
   ```
   Run → Run 'UserServiceApplication'
   ```

2. **Product Service** (8082)
   ```
   Run → Run 'ProductServiceApplication'
   ```

3. **Order Service** (8083)
   ```
   Run → Run 'OrderServiceApplication'
   ```

4. **Payment Service** (8084)
   ```
   Run → Run 'PaymentServiceApplication'
   ```

5. **Notification Service** (8086)
   ```
   Run → Run 'NotificationServiceApplication'
   ```

각 서비스 시작 후 **10-20초 대기**하여 Eureka 등록 완료 확인

---

#### 3️⃣ API Gateway (마지막)
```
Run → Run 'ApiGatewayApplication'
```

**대기**: 콘솔에서 다음 메시지 확인
```
Started ApiGatewayApplication in X seconds
```

---

### 4단계: 확인

#### Eureka Dashboard
http://localhost:8761

**모든 서비스가 UP(초록색)으로 표시되어야 함:**
- ✅ EUREKA-SERVER
- ✅ USER-SERVICE
- ✅ PRODUCT-SERVICE
- ✅ ORDER-SERVICE
- ✅ PAYMENT-SERVICE
- ✅ NOTIFICATION-SERVICE
- ✅ API-GATEWAY

#### Health Check
```bash
# User Service
curl http://localhost:8081/actuator/health

# Product Service
curl http://localhost:8082/actuator/health

# Order Service
curl http://localhost:8083/actuator/health

# Payment Service
curl http://localhost:8084/actuator/health

# API Gateway
curl http://localhost:8080/actuator/health
```

모두 `"status":"UP"` 반환해야 함

#### API Gateway 테스트
```bash
# Product 조회
curl http://localhost:8080/api/products

# User 조회
curl http://localhost:8080/api/users
```

---

## ⚠️ 문제 발생 시

### 포트 충돌
```
Port 808X was already in use
```

**해결:**
```bash
# 해당 포트 사용 중인 프로세스 확인
netstat -ano | findstr :808X

# IntelliJ Run 탭에서 실행 중인 서비스 중지
# 또는 PowerShell로 강제 종료
powershell -Command "Stop-Process -Id <PID> -Force"
```

### 명령줄 길이 오류
```
명령줄이 너무 깁니다
```

**해결:** 이미 수정되었지만, 재발 시:
1. Run → Edit Configurations
2. 해당 서비스 선택
3. Modify options → Shorten command line
4. "JAR manifest" 또는 "@argfile" 선택

### Flyway Migration 실패
```
Schema validation failed
```

**해결:**
1. 데이터베이스가 존재하는지 확인
2. 서비스 재시작
3. 콘솔에서 Flyway 마이그레이션 로그 확인

### Eureka 등록 실패
```
Discovery Client not enabled
```

**해결:**
- 이미 `@EnableDiscoveryClient` 추가됨
- IntelliJ Gradle 프로젝트 새로고침: `Ctrl+Shift+O`
- 서비스 재빌드: `Ctrl+F9`

---

## 📊 서비스 포트 정리

| 서비스 | 포트 | URL |
|--------|------|-----|
| Eureka Server | 8761 | http://localhost:8761 |
| User Service | 8081 | http://localhost:8081 |
| Product Service | 8082 | http://localhost:8082 |
| Order Service | 8083 | http://localhost:8083 |
| Payment Service | 8084 | http://localhost:8084 |
| Notification Service | 8086 | http://localhost:8086 |
| API Gateway | 8080 | http://localhost:8080 |
| Redis | 6379 | - |
| Kafka | 9092 | - |
| Elasticsearch | 9200 | http://localhost:9200 |
| MySQL | 3306 | - |
| PostgreSQL | 5432 | - |

---

## 🛠️ 유용한 명령어

### 모든 서비스 중지
```bash
cd C:\project\livemart
powershell -ExecutionPolicy Bypass -File stop-all-services.ps1
```

### Gradle 빌드
```bash
cd C:\project\livemart
./gradlew build -x test
```

### Docker 컨테이너 재시작
```bash
cd C:\project\livemart
docker-compose restart redis kafka elasticsearch
```

---

## ✨ 다음 단계

서비스가 모두 정상 실행되면:

1. **Swagger UI 확인**
   - Product Service: http://localhost:8082/swagger-ui.html
   - Order Service: http://localhost:8083/swagger-ui.html

2. **테스트 스크립트 실행**
   ```bash
   cd C:\project\livemart
   powershell -File test-saga-final.ps1
   ```

3. **보상 트랜잭션 테스트**
   - `LIVEMART_PROJECT_CONTEXT.md` 문서 참조

---

**작성일**: 2026-02-11
**최종 수정**: API Gateway @EnableDiscoveryClient 추가, 명령줄 길이 오류 수정 완료
