# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-jammy

# postgresql-client provides pg_dump, which the Backup module (Phase 5 Step 6)
# shells out to via ProcessBuilder — without it, backups fail only in the
# container even though they work on a local dev machine with Postgres tools
# installed. curl backs the HEALTHCHECK below.
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client curl \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --shell /usr/sbin/nologin appuser
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Pre-create the storage mount point and the logback-spring.xml log
# directory with the right owner - a fresh named volume inherits ownership
# from the image directory it's first mounted over, and appuser (non-root)
# needs write access to /var/log/kbv-education for the rolling file appender.
RUN mkdir -p /data/storage /var/log/kbv-education \
    && chown -R appuser:appuser /app /data/storage /var/log/kbv-education
USER appuser

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
