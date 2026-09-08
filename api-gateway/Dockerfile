FROM gradle:8.11-jdk21-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x ./gradlew && ./gradlew :api-gateway:bootJar -x test --no-daemon -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/api-gateway/build/libs/api-gateway-2.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["/bin/sh", "-c", "java --enable-preview -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -jar app.jar --spring.profiles.active=render 2>&1"]
