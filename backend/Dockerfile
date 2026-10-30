# syntax=docker/dockerfile:1

# Stage 1: Build artifact with Maven
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Cache Maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build application package skipping unit tests (tests are validated in CI)
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal JRE runtime
FROM eclipse-temurin:17-jre-alpine AS runner
WORKDIR /app

# Run as non-root user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
