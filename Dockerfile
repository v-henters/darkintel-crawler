# ===== Stage 1: Build =====
FROM gradle:8.9-jdk21-alpine AS build
WORKDIR /app

COPY . .
RUN gradle --no-daemon shadowJar

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy fat JAR
COPY --from=build /app/build/libs/darkweb-crawler-all.jar /app/darkweb-crawler-all.jar

# Environment variables (can be overridden by docker-compose)
ENV DARKWEB_CONFIG_PATH=/app/config.toml \
    DARKWEB_REDIS_URI=redis://redis:6379 \
    DARKWEB_AWS_REGION=us-east-1

# Default: use DARKWEB_CONFIG_PATH, and allow override of args
ENTRYPOINT ["java", "-jar", "/app/darkweb-crawler-all.jar"]
