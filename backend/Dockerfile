# ===========================================================================
# Brok's Forge - Backend Dockerfile (multi-stage)
# ===========================================================================

# --------------------------- Build stage -----------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies first for faster incremental builds
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Build the application
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --------------------------- Runtime stage ---------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as a non-root user
RUN addgroup -S forge && adduser -S forge -G forge

COPY --from=build /workspace/target/broks-forge-api.jar app.jar

USER forge

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
