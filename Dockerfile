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
    && ./gradlew bootJar -x test -x spotlessCheck --no-daemon

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app app

COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=60s \
  CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
