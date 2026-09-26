# ---------- 1. Build the React UI ----------
FROM node:22-slim AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---------- 2. Build the Spring Boot jar (UI bundled as static files) ----------
FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q -B package -DskipTests

# ---------- 3. Slim runtime ----------
FROM eclipse-temurin:21-jre
RUN useradd --system --create-home app
WORKDIR /app
COPY --from=api /app/target/google-coffee.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
