FROM maven:3.9-amazoncorretto-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM amazoncorretto:21-alpine
WORKDIR /app
RUN apk add --no-cache curl

COPY --from=build /app/target/*.jar app.jar

ENV PORT=8080
EXPOSE 8080
# Sized for a 512MB container (e.g. Render's free tier) — -Xmx768m alone used to exceed
# that, guaranteeing an OOM kill regardless of actual usage. MaxRAMPercentage lets the JVM
# size the heap off the container's real cgroup memory limit instead of a hardcoded value
# that silently stops matching whatever plan this actually runs on; the explicit caps below
# bound the non-heap regions (metaspace, code cache, thread stacks) that would otherwise be
# uncapped. SerialGC over G1 since free-tier instances typically have ~0.1-0.5 vCPU, where
# G1's extra GC threads cost more than they save.
ENV JAVA_OPTS="-Duser.timezone=UTC -XX:+UseContainerSupport -XX:MaxRAMPercentage=50.0 -XX:InitialRAMPercentage=35.0 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -Xss512k"

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -f http://localhost:${PORT}/api/v1/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT}"]
