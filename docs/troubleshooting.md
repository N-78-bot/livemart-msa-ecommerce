# LiveMart 서버 구동 문제 해결 가이드

## 현재 상태 (2026-02-11 13:50)

### ✅ 모든 문제 해결 완료!

**해결된 문제:**
1. ✅ API Gateway Eureka 등록 실패 → `@EnableDiscoveryClient` 추가로 해결
2. ✅ 명령줄 길이 오류 → 모든 Run Configuration에 `ARGS_FILE` 옵션 추가로 해결
3. ✅ 포트 충돌 → 기존 프로세스 종료 완료

**정상 작동 확인:**
- ✅ Eureka Server (8761)
- ✅ User Service (8081)
- ✅ Product Service (8082)
- ✅ Order Service (8083)
- ✅ Payment Service (8084)
- ✅ Notification Service (8086)
- ✅ API Gateway (8080)
- ✅ Redis (6379)
- ✅ Kafka (9092)
- ✅ Elasticsearch (9200)
- ✅ PostgreSQL (5432) - Payment DB

**서비스 시작 가이드:** `START_SERVICES_GUIDE.md` 참조

---

## 문제 1: API Gateway DOWN 상태

### 증상
```
Eureka Dashboard: API-GATEWAY - DOWN (빨간색)
Health Check: {"status":"DOWN","description":"Remote status from Eureka server"}
```

### 원인
API Gateway가 Eureka에 제대로 등록되지 않음

### 해결 방법 ✅ (이미 수정됨)

1. **ApiGatewayApplication.java에 @EnableDiscoveryClient 추가됨**
   ```java
   @SpringBootApplication
   @EnableDiscoveryClient  // ✅ 추가됨
   public class ApiGatewayApplication {
   ```

2. **IntelliJ에서 API Gateway 재시작**
   - IntelliJ Run/Debug Configurations에서 `ApiGatewayApplication` 중지
   - 다시 시작
   - Gradle refresh: `Ctrl+Shift+O` (Windows)

3. **확인 방법**
   ```bash
   # Health check
   curl http://localhost:8080/actuator/health

   # Eureka 확인
   # http://localhost:8761 접속
   # API-GATEWAY가 UP(초록색)으로 표시되어야 함
   ```

---

## 문제 2: MySQL 데이터베이스 누락 (잠재적 문제)

### 필요한 데이터베이스
- `userdb` - User Service
- `productdb` - Product Service
- `orderdb` - Order Service

### 확인 방법

#### IntelliJ Database Tools 사용
1. IntelliJ 우측 Database 탭 클릭
2. `+` → Data Source → MySQL 클릭
3. 연결 정보 입력:
   ```
   Host: localhost
   Port: 3306
   User: root
   Password: root
   ```
4. "Test Connection" 클릭 → 성공 확인
5. 데이터베이스 목록에서 `userdb`, `productdb`, `orderdb` 확인

### 해결 방법

#### 방법 1: IntelliJ Database Tools로 생성 (권장)

1. IntelliJ Database 탭에서 MySQL 연결 열기
2. 우클릭 → New → Database 클릭
3. 데이터베이스 이름 입력:
   - `userdb`
   - `productdb`
   - `orderdb`
4. Charset: `utf8mb4`, Collation: `utf8mb4_unicode_ci`

#### 방법 2: MySQL Workbench 사용

```sql
CREATE DATABASE IF NOT EXISTS userdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS productdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS orderdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 방법 3: 명령줄 (CMD)

```cmd
# MySQL 경로가 환경변수에 있는 경우
mysql -u root -proot < init-databases.sql

# 전체 경로 지정 (MySQL 8.0 기준)
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -proot < init-databases.sql
```

**참고:** `init-databases.sql` 파일이 이미 생성되어 있습니다.

---

## 문제 3: Flyway Migration 실패

### 증상
```
Error: Flyway validation failed
Error: Schema-validation: missing table [XXX]
```

### 원인
데이터베이스는 존재하지만 테이블이 없음

### 해결 방법

#### Option 1: Flyway 마이그레이션 실행 (권장)
1. 데이터베이스가 존재하는지 확인
2. IntelliJ에서 서비스 재시작
3. Flyway가 자동으로 테이블 생성

#### Option 2: ddl-auto 임시 변경 (개발 환경만)
```yaml
# application.yml
spring:
  jpa:
    hibernate:
      ddl-auto: update  # validate → update로 변경
```

**주의:** 프로덕션에서는 절대 `ddl-auto: update` 사용 금지!

---

## 문제 4: Redis 연결 실패

### 증상
```
Cannot connect to Redis at localhost:6379
```

### 확인
```bash
docker ps | findstr redis
```

### 해결
```bash
# Redis 컨테이너 시작
cd livemart
docker-compose up -d redis
```

---

## 문제 5: Kafka 연결 실패

### 증상
```
Cannot connect to Kafka broker
Connection to node -1 could not be established
```

### 확인
```bash
docker ps | findstr kafka
docker ps | findstr zookeeper
```

### 해결
```bash
# Kafka & Zookeeper 시작 (순서 중요!)
cd livemart
docker-compose up -d zookeeper
# 5초 대기
docker-compose up -d kafka
```

---

## 완전한 서비스 시작 순서 (처음부터)

### 1단계: 인프라 시작

```bash
cd C:\project\livemart

# 모든 인프라 서비스 시작
docker-compose up -d redis zookeeper kafka elasticsearch
```

**PostgreSQL도 필요한 경우:**
```bash
docker-compose up -d postgres-payment
```

### 2단계: 데이터베이스 생성

IntelliJ Database Tools 또는 MySQL Workbench로:
- `userdb`
- `productdb`
- `orderdb`

### 3단계: 서비스 시작 (IntelliJ)

**시작 순서:**
1. **Eureka Server** (8761) - 먼저 시작 필수!
2. 나머지 서비스들 (순서 무관):
   - User Service (8081)
   - Product Service (8082)
   - Order Service (8083)
   - Payment Service (8084)
   - Notification Service (8086)
3. **API Gateway** (8080) - 마지막 시작

**IntelliJ에서 실행:**
- `Shift + F10` - 현재 설정 실행
- 또는 Application 클래스 우클릭 → Run

### 4단계: 확인

```bash
# Eureka Dashboard
http://localhost:8761

# 모든 서비스가 UP(초록색)인지 확인
# API Gateway 테스트
curl http://localhost:8080/api/products
```

---

## 포트 충돌 문제

### 증상
```
Port 8080 is already in use
```

### 확인
```bash
netstat -ano | findstr :8080
```

### 해결
```bash
# 프로세스 ID 확인 후 종료
taskkill /PID <프로세스ID> /F
```

---

## 유용한 명령어

### 인프라 상태 확인
```bash
# Docker 컨테이너 확인
docker ps

# 특정 컨테이너 로그
docker logs livemart-redis
docker logs livemart-kafka
docker logs livemart-elasticsearch

# Redis 연결 테스트
docker exec -it livemart-redis redis-cli ping
```

### 포트 확인
```bash
# 모든 사용 중인 포트
netstat -ano | findstr "8080 8081 8082 8083 8084 8086 8761 3306 6379 9092 9200"
```

### Gradle 빌드
```bash
cd C:\project\livemart

# 전체 빌드 (테스트 제외)
./gradlew build -x test

# 특정 서비스만 빌드
./gradlew :api-gateway:build -x test
```

---

## 빠른 체크리스트

서비스 실행 전 확인:

- [ ] Docker Desktop 실행 중
- [ ] MySQL 실행 중 (로컬 또는 Docker)
- [ ] Redis 컨테이너 실행 중
- [ ] Kafka & Zookeeper 컨테이너 실행 중
- [ ] Elasticsearch 컨테이너 실행 중
- [ ] PostgreSQL 실행 중 (Payment Service용)
- [ ] 데이터베이스 생성됨 (userdb, productdb, orderdb)
- [ ] 포트 충돌 없음 (8080-8086, 8761, 3306, 6379, 9092)

---

## 문제 지속 시

1. **로그 확인**
   - IntelliJ Run 탭에서 오류 메시지 확인
   - 빨간색 스택 트레이스 찾기

2. **단계별 디버깅**
   - Eureka Server만 먼저 시작
   - 서비스 하나씩 추가
   - 각 서비스가 Eureka에 등록되는지 확인

3. **캐시 정리**
   ```bash
   # Gradle 캐시 정리
   ./gradlew clean build -x test

   # IntelliJ 캐시
   # File → Invalidate Caches → Invalidate and Restart
   ```

4. **문서 참조**
   - `LIVEMART_PROJECT_CONTEXT.md` - 전체 프로젝트 컨텍스트
   - 각 서비스의 `README.md`

---

## 연락처

문제가 계속되면 오류 로그와 함께 문의:
- GitHub Issues
- 프로젝트 개발자: 박민제

---

**마지막 업데이트:** 2026-02-11
**상태:** API Gateway @EnableDiscoveryClient 추가 완료
