# 🚀 LiveMart 완전 설정 및 실행 가이드

## 📋 목차
1. [근본 문제 및 해결](#근본-문제-및-해결)
2. [전체 시스템 아키텍처](#전체-시스템-아키텍처)
3. [완전한 설정 절차](#완전한-설정-절차)
4. [문제 해결 가이드](#문제-해결-가이드)

---

## 🔍 근본 문제 및 해결

### 발견된 문제들:

#### 1. MySQL Connector/J 인증 플러그인 버그
**문제**: MySQL Connector/J 8.x 전체의 인증 플러그인 협상 버그
```
Error: !AuthenticationProvider.BadAuthenticationPlugin!
```

**해결**: MySQL 5.7과 완벽 호환되는 레거시 드라이버(5.1.49) 사용
```gradle
runtimeOnly 'mysql:mysql-connector-java:5.1.49'
```
```yaml
driver-class-name: com.mysql.jdbc.Driver
```

#### 2. 서비스별 DB 계정 불일치
**문제**: user/product/order 각각 다른 계정 사용 → 인증 복잡도 증가

**해결**: 모든 서비스를 root 계정으로 통일
```yaml
username: root
password: root123
```

#### 3. Docker MySQL 컨테이너 상태 불일치
**문제**: 이전 볼륨 데이터로 인한 인증 플러그인 충돌

**해결**: 모든 MySQL 컨테이너 완전 재생성
```bash
docker rm -f livemart-mysql-*
docker volume rm docker_mysql-*-data
docker-compose up -d
```

#### 4. IntelliJ 캐시 문제
**문제**: Ultimate → Community 전환 시 캐시 충돌

**해결**: `.idea`, `.gradle`, `build` 디렉토리 완전 삭제

---

## 🏗️ 전체 시스템 아키텍처

### 포트 할당 (고정):
```
Infrastructure:
├─ Eureka Server        : 8761
├─ API Gateway          : 8080
├─ Config Server        : 8888
│
Services:
├─ User Service         : 8081
├─ Product Service      : 8082
├─ Order Service        : 8083
├─ Payment Service      : 8084
├─ Inventory Service    : 8085
├─ Notification Service : 8086
├─ Analytics Service    : 8087
│
Databases:
├─ MySQL User           : 3306
├─ MySQL Product        : 3317
├─ MySQL Order          : 3318
├─ PostgreSQL Inventory : 5432
├─ PostgreSQL Analytics : 5433
│
Middleware:
├─ Redis                : 6379
├─ Kafka                : 9092
├─ Zookeeper            : 2181
├─ Elasticsearch        : 9200
│
Monitoring:
├─ Prometheus           : 9090
├─ Grafana              : 3000
├─ Kibana               : 5601
└─ Zipkin               : 9411
```

### MySQL 서비스별 DB:
```
User Service    → userdb    (localhost:3306)
Product Service → productdb (localhost:3317)
Order Service   → orderdb   (localhost:3318)
```

---

## ⚙️ 완전한 설정 절차

### Step 0: 사전 준비

#### A. 필수 소프트웨어 확인
```powershell
# Java 21
java -version

# Docker Desktop
docker --version

# Node.js (프론트엔드용)
node -v
npm -v
```

#### B. IntelliJ Community Edition 설치
```
https://www.jetbrains.com/idea/download/
→ Community Edition (무료)
```

---

### Step 1: IntelliJ 완전 초기화

#### PowerShell에서 실행:
```powershell
cd C:\project\livemart
.\clean-intellij.bat
```

**또는 수동으로:**
```powershell
# Gradle 빌드 디렉토리 삭제
Get-ChildItem -Path . -Recurse -Directory -Filter "build" | Remove-Item -Recurse -Force

# IntelliJ 설정 삭제
Remove-Item -Path ".idea" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path ".gradle" -Recurse -Force -ErrorAction SilentlyContinue
Get-ChildItem -Path . -Recurse -Filter "*.iml" | Remove-Item -Force
```

---

### Step 2: Docker 인프라 시작

```powershell
# 기존 컨테이너 정리 (필요시)
docker-compose -f C:\project\livemart\docker\docker-compose.yml down -v

# 전체 인프라 시작
docker-compose -f C:\project\livemart\docker\docker-compose.yml up -d

# 상태 확인
docker ps

# MySQL 준비 대기 (30초)
timeout /t 30
```

**확인:**
```powershell
# MySQL 연결 테스트
docker exec livemart-mysql-user mysql -uroot -proot123 -e "SHOW DATABASES;"
docker exec livemart-mysql-product mysql -uroot -proot123 -e "SHOW DATABASES;"
docker exec livemart-mysql-order mysql -uroot -proot123 -e "SHOW DATABASES;"
```

---

### Step 3: IntelliJ 프로젝트 열기

#### 1. IntelliJ IDEA 실행

#### 2. 프로젝트 열기
```
File → Open → C:\project\livemart
```

#### 3. Trust Project 선택
```
Trust Project 버튼 클릭
```

#### 4. Annotation Processing 활성화 ⚠️ 중요!
```
File → Settings (Ctrl + Alt + S)
→ Build, Execution, Deployment
  → Compiler
    → Annotation Processors
      → ✅ Enable annotation processing
```

#### 5. Gradle 동기화 대기
```
우측 하단 "Indexing..." 완료까지 대기 (1~2분)
```

#### 6. Gradle 수동 새로고침
```
우측 Gradle 탭 → 🔄 새로고침 클릭
모든 의존성 다운로드 완료까지 대기
```

#### 7. 전체 프로젝트 빌드
```
Build → Rebuild Project
또는 Ctrl + Shift + F9
```

---

### Step 4: 서비스 실행 (순서 중요!)

#### 실행 순서:

**1. Eureka Server (8761)** - 먼저 실행!
```
eureka-server/src/main/java/com/livemart/eureka/EurekaServerApplication.java
→ 우클릭 → Run 'EurekaServerApplication'
→ ⏰ 30초 대기! (완전히 시작될 때까지)
```

**성공 메시지:**
```
Started EurekaServerApplication in X.XX seconds
```

**확인:**
```
http://localhost:8761
→ Eureka Dashboard 표시되어야 함
```

---

**2. API Gateway (8080)**
```
api-gateway/src/main/java/com/livemart/gateway/ApiGatewayApplication.java
→ 우클릭 → Run 'ApiGatewayApplication'
```

**성공 메시지:**
```
Started ApiGatewayApplication in X.XX seconds
Registered instance API-GATEWAY with status UP
```

---

**3. User Service (8081)**
```
user-service/src/main/java/com/livemart/user/UserServiceApplication.java
→ 우클릭 → Run 'UserServiceApplication'
```

**성공 메시지:**
```
Started UserServiceApplication in X.XX seconds
Registered instance USER-SERVICE with status UP
```

**문제 발생 시:**
- Gradle 새로고침 다시 실행
- mysql-connector-j:8.0.33 버전 확인
- MySQL 컨테이너 재시작: `docker restart livemart-mysql-user`

---

**4. Product Service (8082)**
```
product-service/src/main/java/com/livemart/product/ProductServiceApplication.java
→ 우클릭 → Run 'ProductServiceApplication'
```

---

**5. Order Service (8083)**
```
order-service/src/main/java/com/livemart/order/OrderServiceApplication.java
→ 우클릭 → Run 'OrderServiceApplication'
```

---

**6. Analytics Service (8087)**
```
analytics-service/src/main/java/com/livemart/analytics/AnalyticsServiceApplication.java
→ 우클릭 → Run 'AnalyticsServiceApplication'
```

---

### Step 5: 전체 시스템 확인

#### Eureka Dashboard
```
http://localhost:8761
```

**확인사항:**
- ✅ API-GATEWAY (8080) - UP
- ✅ USER-SERVICE (8081) - UP
- ✅ PRODUCT-SERVICE (8082) - UP
- ✅ ORDER-SERVICE (8083) - UP
- ✅ ANALYTICS-SERVICE (8087) - UP

#### Health Checks
```
http://localhost:8080/actuator/health
http://localhost:8081/actuator/health
http://localhost:8082/actuator/health
http://localhost:8083/actuator/health
http://localhost:8087/actuator/health
```

#### Swagger API 문서
```
http://localhost:8081/swagger-ui.html  (User Service)
http://localhost:8082/swagger-ui.html  (Product Service)
http://localhost:8083/swagger-ui.html  (Order Service)
```

---

### Step 6: 프론트엔드 실행

```powershell
cd C:\project\livemart\frontend
npm install
npm run dev
```

**또는:**
```
C:\project\livemart\start-frontend.bat 더블클릭
```

**접속:**
```
http://localhost:3000
```

---

## 🐛 문제 해결 가이드

### 문제 1: MySQL 인증 에러

**증상:**
```
!AuthenticationProvider.BadAuthenticationPlugin!
```

**해결:**
```powershell
# 1. MySQL Connector 레거시 버전으로 변경
# user-service/build.gradle, product-service/build.gradle, order-service/build.gradle
runtimeOnly 'mysql:mysql-connector-java:5.1.49'

# 2. application.yml에서 드라이버 클래스 변경
driver-class-name: com.mysql.jdbc.Driver

# 3. MySQL 컨테이너 재생성
docker stop livemart-mysql-user livemart-mysql-product livemart-mysql-order
docker rm livemart-mysql-user livemart-mysql-product livemart-mysql-order
docker volume rm docker_mysql-user-data docker_mysql-product-data docker_mysql-order-data
docker-compose -f C:\project\livemart\docker\docker-compose.yml up -d

# 4. IntelliJ Gradle 새로고침
우측 Gradle 탭 → 🔄 새로고침
```

---

### 문제 2: Lombok getter/setter/builder 에러

**증상:**
```
error: cannot find symbol
  symbol: method builder()
  symbol: method getId()
```

**해결:**
```
File → Settings → Build, Execution, Deployment
  → Compiler → Annotation Processors
    → ✅ Enable annotation processing
    → Apply → OK

우측 Gradle 탭 → 🔄 새로고침
Build → Rebuild Project
```

---

### 문제 3: 포트 충돌

**증상:**
```
Port 8080 was already in use
```

**해결:**
```powershell
# 포트 사용 중인 프로세스 확인
netstat -ano | findstr :8080

# 프로세스 종료 (PID 확인 후)
taskkill /PID [PID번호] /F
```

---

### 문제 4: Eureka에 서비스 등록 안 됨

**해결:**
```yaml
# application.yml 확인
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
```

**Eureka Server가 먼저 시작되었는지 확인:**
```
http://localhost:8761
```

---

### 문제 5: Docker 컨테이너가 시작되지 않음

**해결:**
```powershell
# Docker Desktop 재시작

# 로그 확인
docker logs livemart-mysql-user
docker logs livemart-redis
docker logs livemart-kafka

# 완전 재시작
docker-compose -f C:\project\livemart\docker\docker-compose.yml down -v
docker-compose -f C:\project\livemart\docker\docker-compose.yml up -d
```

---

## 📊 시스템 상태 자동 확인

```powershell
cd C:\project\livemart
.\check-system.bat
```

---

## 🔧 유용한 스크립트

### IntelliJ 캐시 정리
```powershell
.\clean-intellij.bat
```

### 시스템 상태 확인
```powershell
.\check-system.bat
```

### 모든 서비스 중지
```powershell
# Docker 컨테이너 중지
docker-compose -f C:\project\livemart\docker\docker-compose.yml down

# IntelliJ에서 각 서비스 Stop
```

---

## 📝 개발 워크플로우

### 매일 시작 시:
1. Docker Desktop 실행 확인
2. `.\check-system.bat` 실행
3. IntelliJ 열기
4. Eureka → Gateway → Services 순서로 실행

### 코드 변경 후:
1. 변경된 서비스만 Restart
2. Eureka Dashboard에서 UP 상태 확인
3. Swagger로 API 테스트

### 문제 발생 시:
1. `.\check-system.bat`로 상태 확인
2. 해당 서비스 로그 확인
3. 필요시 MySQL 컨테이너 재시작
4. 최후의 수단: `.\clean-intellij.bat` 실행

---

## ✅ 체크리스트

### 초기 설정:
- [ ] Java 21 설치
- [ ] Docker Desktop 설치 및 실행
- [ ] Node.js 설치
- [ ] IntelliJ Community Edition 설치
- [ ] `.\clean-intellij.bat` 실행
- [ ] Annotation Processing 활성화

### 매번 실행:
- [ ] Docker 컨테이너 모두 실행 중
- [ ] Eureka Server 먼저 시작 (30초 대기)
- [ ] 나머지 서비스 순차 실행
- [ ] Eureka Dashboard에서 모두 UP 확인

---

**🎉 이제 LiveMart가 정상 작동합니다!**
