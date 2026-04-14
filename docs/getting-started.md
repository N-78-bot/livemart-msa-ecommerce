# 🚀 LiveMart 실행 가이드 (IntelliJ 없이!)

## 📋 준비사항 체크리스트

- ✅ Docker Desktop 실행 중
- ✅ Java 21 설치 확인: `java -version`
- ⚠️ Node.js 설치 필요 (프론트엔드용): https://nodejs.org/

---

## 🐳 Step 1: Docker 인프라 시작 (이미 완료!)

```powershell
docker ps
```

**실행 중이어야 할 서비스:**
- MySQL User (3306)
- MySQL Product (3317)
- MySQL Order (3318)
- PostgreSQL Inventory (5432)
- PostgreSQL Analytics (5433)
- Redis (6379)
- Kafka (9092)
- Zookeeper (2181)
- Elasticsearch (9200)
- Kibana (5601)
- Prometheus (9090)
- Grafana (3000)
- Zipkin (9411)

**만약 중지되었다면:**
```powershell
docker-compose -f C:\project\livemart\docker\docker-compose.yml up -d
```

---

## ⚙️ Step 2: 백엔드 서비스 실행

### 방법 A: 배치 파일 사용 (제일 쉬움!)

**각 파일을 더블클릭하거나 PowerShell에서 실행:**

```powershell
# 1. Eureka Server (먼저 실행, 30초 대기)
C:\project\livemart\start-eureka.bat

# 2. API Gateway (새 터미널)
C:\project\livemart\start-gateway.bat

# 3. User Service (새 터미널)
C:\project\livemart\start-user-service.bat

# 4. Product Service (새 터미널)
C:\project\livemart\start-product-service.bat

# 5. Order Service (새 터미널)
C:\project\livemart\start-order-service.bat

# 6. Analytics Service (새 터미널)
C:\project\livemart\start-analytics-service.bat
```

### 방법 B: Gradle 직접 실행

```powershell
cd C:\project\livemart

# Eureka Server
.\gradlew.bat :eureka-server:bootRun

# 새 PowerShell 창에서
.\gradlew.bat :api-gateway:bootRun

# 새 PowerShell 창에서
.\gradlew.bat :product-service:bootRun
```

---

## ✅ Step 3: 백엔드 실행 확인

### Eureka Dashboard
```
http://localhost:8761
```
→ 모든 서비스가 **UP** 상태로 보여야 함!

### 각 서비스 Health Check
```
http://localhost:8080/actuator/health  (API Gateway)
http://localhost:8081/actuator/health  (User Service)
http://localhost:8082/actuator/health  (Product Service)
http://localhost:8083/actuator/health  (Order Service)
http://localhost:8087/actuator/health  (Analytics Service)
```

### Swagger API 문서
```
http://localhost:8082/swagger-ui.html  (Product Service)
http://localhost:8081/swagger-ui.html  (User Service)
http://localhost:8083/swagger-ui.html  (Order Service)
```

---

## 🎨 Step 4: 프론트엔드 실행

### Node.js 설치 확인
```powershell
node -v
npm -v
```

**설치 안 되어 있다면:**
https://nodejs.org/ → v20.x LTS 다운로드

### 프론트엔드 시작
```powershell
cd C:\project\livemart\frontend
npm install
npm run dev
```

**또는 배치 파일 더블클릭:**
```
C:\project\livemart\start-frontend.bat
```

### 접속
```
http://localhost:3000
```

---

## 📊 모니터링 & 관리 도구

### Grafana Dashboard
```
http://localhost:3000
ID: admin / PW: admin
```

### Prometheus Metrics
```
http://localhost:9090
```

### Kibana (Elasticsearch)
```
http://localhost:5601
```

### Zipkin Tracing
```
http://localhost:9411
```

---

## 🛑 서비스 종료

### 백엔드 종료
각 PowerShell 창에서 `Ctrl + C`

### Docker 전체 종료
```powershell
docker-compose -f C:\project\livemart\docker\docker-compose.yml down
```

---

## 🐛 트러블슈팅

### 포트 이미 사용 중
```powershell
# 포트 확인
netstat -ano | findstr :8080

# 프로세스 종료
taskkill /PID [PID번호] /F
```

### Gradle 빌드 실패
```powershell
# 캐시 삭제 후 재빌드
.\gradlew.bat clean build -x test
```

### Docker 컨테이너 재시작
```powershell
# 전체 재시작
docker-compose -f C:\project\livemart\docker\docker-compose.yml restart

# 특정 서비스만 재시작
docker restart livemart-redis
docker restart livemart-kafka
```

---

## 🎯 개발 도구 추천

### IntelliJ Community Edition (무료)
https://www.jetbrains.com/idea/download/
- Spring Boot 완벽 지원
- Gradle 통합
- Git GUI

### VS Code (무료)
https://code.visualstudio.com/
- Extension Pack for Java
- Spring Boot Extension Pack
- 가볍고 빠름

**VS Code 설정 가이드:**
```
C:\project\livemart\VSCODE-SETUP.md
```

---

## 📝 개발 워크플로우

1. **Docker 인프라 시작**
2. **Eureka Server 실행** (30초 대기)
3. **나머지 백엔드 서비스 실행**
4. **Eureka Dashboard 확인** (모든 서비스 UP)
5. **프론트엔드 실행**
6. **개발 시작!** 🎉

---

## 🔥 핫 팁

### 빠른 재시작
```powershell
# 백엔드만 재빌드 (테스트 제외)
.\gradlew.bat clean build -x test

# 특정 서비스만 재빌드
.\gradlew.bat :product-service:clean :product-service:build -x test
```

### 로그 확인
```powershell
# Docker 로그
docker logs livemart-mysql-product -f

# Gradle 빌드 로그
.\gradlew.bat :product-service:bootRun --info
```

### 성능 모니터링
```
Grafana: http://localhost:3000
→ LiveMart Overview Dashboard
→ 실시간 RPS, 응답시간, 에러율 확인
```

---

**🚀 Happy Coding!**
