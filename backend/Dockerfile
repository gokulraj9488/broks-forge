# ===========================================================================
# Brok's Forge - Backend Dockerfile (multi-stage)
# ===========================================================================

# --------------------------- Build stage -----------------------------------
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /workspace

# Platform V2 modules are local (not on Maven Central). Build and install them into the image's
# local Maven repository first, so the application below can resolve them. This is an implementation
# detail of the image build only — the developer workflow (docker compose up --build) is unchanged.
COPY kernel ./kernel
COPY forge-knowledge ./forge-knowledge
COPY forge-fvcs ./forge-fvcs
COPY forge-fkge ./forge-fkge
COPY forge-fxp ./forge-fxp
RUN mvn -B -q -f kernel/pom.xml install -DskipTests \
 && mvn -B -q -f forge-knowledge/pom.xml install -DskipTests \
 && mvn -B -q -f forge-fvcs/pom.xml install -DskipTests \
 && mvn -B -q -f forge-fkge/pom.xml install -DskipTests \
 && mvn -B -q -f forge-fxp/pom.xml install -DskipTests

# Cache application dependencies for faster incremental builds
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
