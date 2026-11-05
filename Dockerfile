FROM node:22-bookworm-slim AS frontend-check
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN node --test src/*.test.js && npm run lint && npm run build

FROM eclipse-temurin:21-jdk AS builder
WORKDIR /workspace
COPY backend/ ./
COPY --from=frontend-check /frontend/dist /tmp/verified-frontend
RUN chmod +x gradlew && ./gradlew test bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /workspace/build/libs/*.jar app.jar
ENV PORT=10000
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k"
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
