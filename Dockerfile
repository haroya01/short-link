# Build stage runs on the GitHub runner's native arch ($BUILDPLATFORM = linux/amd64) — no QEMU
# emulation, so Gradle / annotation processing / Spotless finish in ~3 min instead of stalling
# 20-30 min under arm64 emulation. The resulting JAR is arch-neutral and gets copied into the
# arm64 runtime stage below, which only has to run COPY + apt-get (tiny under emulation).
FROM --platform=$BUILDPLATFORM gradle:8.10-jdk21 AS build
WORKDIR /workspace

# Copy build descriptors first so the slow Gradle dependency layer can cache by itself —
# changes to src/ or the MaxMind ARG below don't bust this layer.
COPY build.gradle settings.gradle gradlew ./
COPY gradle gradle
RUN ./gradlew --version --no-daemon

# Source comes after deps so editing code doesn't redownload deps.
COPY src src

# ARG is declared right before the RUN that uses it: changing the key value only invalidates
# this final layer, leaving deps + src caches intact for fast incremental rebuilds.
ARG MAXMIND_LICENSE_KEY
RUN if [ -n "${MAXMIND_LICENSE_KEY:-}" ]; then \
      MAXMIND_LICENSE_KEY="${MAXMIND_LICENSE_KEY}" ./gradlew downloadGeoLite2 --no-daemon; \
      MAXMIND_LICENSE_KEY="${MAXMIND_LICENSE_KEY}" ./gradlew downloadGeoLite2Asn --no-daemon || echo "ASN download failed, continuing without it"; \
    fi \
    && ./gradlew bootJar -x test -x spotlessCheck --no-daemon \
    && java -Djarmode=tools -jar build/libs/*.jar extract \
      --destination build/extracted --application-filename app.jar

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app app \
    && chown app:app /app

COPY --from=build --chown=app:app /workspace/build/extracted/ ./
USER app

# A CDS archive maps only into the JVM and class path that wrote it, so it is written here. Training
# exits once the context has refreshed, before anything opens a connection, so it needs no database
# or Redis: Flyway and Hibernate's schema checks are off and the prod placeholders get dummy values.
RUN java -XX:ArchiveClassesAtExit=app.jsa -Xlog:cds=error -Xlog:cds+dynamic=error \
      -Dspring.context.exit=onRefresh -Dspring.profiles.active=prod \
      -Dspring.flyway.enabled=false -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -DDB_URL=jdbc:mysql://localhost:3306/cds -DDB_USERNAME=cds -DDB_PASSWORD=cds \
      -DREDIS_HOST=localhost -DSHORT_LINK_BASE_URL=http://localhost \
      -DJWT_PRIVATE_KEY= -DJWT_PUBLIC_KEY= \
      -jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=55", \
  "-XX:SharedArchiveFile=app.jsa", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
