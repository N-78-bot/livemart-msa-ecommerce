#!/usr/bin/env bash
# =====================================================================
# LiveMart MSA — 백엔드 서비스 시작 스크립트 (Linux/macOS)
#
# 사용법:
#   1. .env 파일의 CHANGE_ME 값들을 실제 값으로 채우세요
#   2. docker-compose -f docker-compose.infra.yml up -d
#   3. chmod +x start-backend.sh && ./start-backend.sh
# =====================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# .env 파일 로드
if [ -f "$SCRIPT_DIR/.env" ]; then
    set -a
    source "$SCRIPT_DIR/.env"
    set +a
    echo "[OK] .env 파일 로드 완료"
else
    echo "[ERROR] .env 파일이 없습니다."
    exit 1
fi

JAR_DIR="$SCRIPT_DIR"
JAVA_OPTS="-Xms256m -Xmx512m"

start_service() {
    local name=$1
    local jar=$2
    shift 2
    echo "▶ $name 시작..."
    java $JAVA_OPTS -jar "$jar" "$@" > "$SCRIPT_DIR/logs/${name}.log" 2>&1 &
    echo "$!" > "$SCRIPT_DIR/logs/${name}.pid"
    echo "  PID: $! | 로그: logs/${name}.log"
}

mkdir -p "$SCRIPT_DIR/logs"

echo ""
echo "========================================"
echo " LiveMart 백엔드 서비스 시작"
echo "========================================"

# 1. Eureka
start_service "eureka" "$JAR_DIR/eureka-server/build/libs/eureka-server-2.0.0.jar"
sleep 15

# 2. API Gateway
start_service "api-gateway" "$JAR_DIR/api-gateway/build/libs/api-gateway-2.0.0.jar" \
    --REDIS_HOST="$REDIS_HOST" \
    --REDIS_PORT="$REDIS_PORT" \
    --CORS_ALLOWED_ORIGINS="$CORS_ALLOWED_ORIGINS"
sleep 10

# 3. User Service
start_service "user-service" "$JAR_DIR/user-service/build/libs/user-service-2.0.0.jar" \
    --DB_HOST="$USER_DB_HOST" \
    --DB_PORT="$USER_DB_PORT" \
    --DB_USERNAME="$USER_DB_USERNAME" \
    --DB_PASSWORD="$DB_PASSWORD" \
    --JWT_SECRET="$JWT_SECRET" \
    --SMTP_USERNAME="$SMTP_USERNAME" \
    --SMTP_PASSWORD="$SMTP_PASSWORD" \
    --KAKAO_CLIENT_ID="$KAKAO_CLIENT_ID" \
    --KAKAO_CLIENT_SECRET="$KAKAO_CLIENT_SECRET" \
    --NAVER_CLIENT_ID="$NAVER_CLIENT_ID" \
    --NAVER_CLIENT_SECRET="$NAVER_CLIENT_SECRET" \
    --GOOGLE_CLIENT_ID="$GOOGLE_CLIENT_ID" \
    --GOOGLE_CLIENT_SECRET="$GOOGLE_CLIENT_SECRET" \
    --OAUTH2_BASE_URL="$OAUTH2_BASE_URL" \
    --oauth2.redirect-uri="$OAUTH2_REDIRECT_URI" \
    --COOKIE_SECURE="$COOKIE_SECURE" \
    --REDIS_HOST="$REDIS_HOST"
sleep 10

# 4. Product Service
start_service "product-service" "$JAR_DIR/product-service/build/libs/product-service-2.0.0.jar" \
    --DB_HOST="$PRODUCT_DB_HOST" \
    --DB_PORT="$PRODUCT_DB_PORT" \
    --DB_USERNAME="$PRODUCT_DB_USERNAME" \
    --DB_PASSWORD="$PRODUCT_DB_PASSWORD" \
    --REDIS_HOST="$REDIS_HOST" \
    --KAFKA_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS"
sleep 10

# 5. Order Service
start_service "order-service" "$JAR_DIR/order-service/build/libs/order-service-2.0.0.jar" \
    --DB_HOST="$ORDER_DB_HOST" \
    --DB_PORT="$ORDER_DB_PORT" \
    --DB_USERNAME="$ORDER_DB_USERNAME" \
    --DB_PASSWORD="$ORDER_DB_PASSWORD" \
    --REDIS_HOST="$REDIS_HOST" \
    --KAFKA_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS" \
    --JWT_SECRET="$JWT_SECRET"
sleep 10

# 6. Payment Service
start_service "payment-service" "$JAR_DIR/payment-service/build/libs/payment-service-2.0.0.jar" \
    --DB_HOST="$PAYMENT_DB_HOST" \
    --DB_PORT="$PAYMENT_DB_PORT" \
    --DB_USERNAME="$PAYMENT_DB_USERNAME" \
    --DB_PASSWORD="$PAYMENT_DB_PASSWORD" \
    --TOSS_CLIENT_KEY="$TOSS_CLIENT_KEY" \
    --TOSS_SECRET_KEY="$TOSS_SECRET_KEY"
sleep 10

# 7. Inventory Service
start_service "inventory-service" "$JAR_DIR/inventory-service/build/libs/inventory-service-2.0.0.jar" \
    --DB_HOST="$INVENTORY_DB_HOST" \
    --DB_PORT="$INVENTORY_DB_PORT" \
    --DB_USERNAME="$INVENTORY_DB_USERNAME" \
    --DB_PASSWORD="$INVENTORY_DB_PASSWORD" \
    --KAFKA_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS"
sleep 5

# 8. AI Service
start_service "ai-service" "$JAR_DIR/ai-service/build/libs/ai-service-2.0.0.jar" \
    --OPENAI_API_KEY="$OPENAI_API_KEY" \
    --OPENROUTER_API_KEY="$OPENROUTER_API_KEY" \
    --REDIS_HOST="$REDIS_HOST" \
    --KAFKA_BOOTSTRAP_SERVERS="$KAFKA_BOOTSTRAP_SERVERS"

echo ""
echo "========================================"
echo " 모든 서비스 시작 완료!"
echo "========================================"
echo " Eureka Dashboard : http://localhost:8761"
echo " API Gateway      : http://localhost:8888"
echo ""
echo " 중지: kill \$(cat logs/*.pid)"
echo " 로그: tail -f logs/user-service.log"
echo "========================================"
