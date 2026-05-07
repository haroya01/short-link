FROM gradle:8.10-jdk17 AS build
ARG MAXMIND_LICENSE_KEY
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY gradlew gradlew
COPY src src
RUN MAXMIND_LICENSE_KEY="${MAXMIND_LICENSE_KEY}" ./gradlew downloadGeoLite2 --no-daemon \
    && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre AS runtime
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
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
