FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="platform@surv.com"
LABEL description="Confidential Document Surveillance Platform"

RUN addgroup -S survgroup && adduser -S survuser -G survgroup

WORKDIR /app

COPY target/surveillance-platform-1.0.0-SNAPSHOT.jar app.jar

RUN mkdir -p logs && chown -R survuser:survgroup /app

USER survuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseG1GC", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-docker}", \
  "-jar", "app.jar"]
