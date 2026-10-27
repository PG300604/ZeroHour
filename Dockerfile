# ── Stage 1: Build Frontend ──────────────────────────────────────────────────
FROM node:22-alpine AS frontend-build
WORKDIR /frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend/ ./
RUN npm run build

# ── Stage 2: Build Backend ───────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS backend-build
WORKDIR /app
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src

# Copy built frontend assets from Stage 1 into backend static resources
COPY --from=frontend-build /frontend/dist ./src/main/resources/static

# Package jar without running tests
RUN mvn clean package -DskipTests -B

# ── Stage 3: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app

# Security: run as non-root user
RUN groupadd -r zerohour && useradd -r -g zerohour zerohour
USER zerohour

# Copy JAR from build stage
COPY --from=backend-build /app/target/zerohour-*.jar app.jar

# JVM tuning for 512MB RAM container limits on Render
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=65.0 -XX:MaxMetaspaceSize=160m -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
