# Production backend image (Phase 11 brief §5/§6/§64). Multi-stage: a Maven/JDK build
# stage that never ships, and a minimal JRE runtime stage with no Maven, no source tree,
# and no build tooling. Pinned image tags throughout - never `latest` (brief §5/§77).
#
# Build (from the backend/ directory, or with -f backend/Dockerfile from the repo root):
#   docker build \
#     --build-arg BUILD_COMMIT=$(git rev-parse HEAD) \
#     -t foreigner-warsaw-backend:<version-or-sha> .
#
# BUILD_COMMIT flows into Spring Boot's BuildProperties (see pom.xml's build-info
# execution) so /api/v1/platform/status reports the real commit, not "unknown" - see
# docs/product/PHASE_10_5_REPORT.md-adjacent PlatformStatusController.

# --- Build stage --------------------------------------------------------------------
# eclipse-temurin ships an official Maven-free JDK image; the project's own Maven
# Wrapper (mvnw, committed - see CLAUDE.md) bootstraps the pinned Maven 3.9.16 itself,
# so no separate Maven base image is needed.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build

# Dependency layer cached separately from source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
ARG BUILD_COMMIT=unknown
RUN ./mvnw -B -Dbuild.commit=${BUILD_COMMIT} -DskipTests package \
    && mv target/*.jar target/app.jar

# --- Runtime stage -------------------------------------------------------------------
FROM eclipse-temurin:25-jre-noble AS runtime

# curl is not present in the base JRE image at all (found by actually running the
# HEALTHCHECK below against a real container - it failed with "executable file not
# found in $PATH", not assumed) - needed only for the HEALTHCHECK's own HTTP call.
# --no-install-recommends plus the apt-cache cleanup keeps this addition small and
# deliberate rather than pulling in whatever else `curl` would otherwise recommend.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Non-root runtime user (brief §5/§64) - a dedicated user/group, not the image's
# already-present "ubuntu"/host-uid-colliding default. 10001 chosen deliberately (not
# 1000) - eclipse-temurin's own noble base already has a GID 1000 ("ubuntu"), found by
# actually building this image, not assumed. Plain (non---system) groupadd/useradd,
# since --system's own implied UID/GID range (<1000) is exactly what 10001 deliberately
# avoids - using --system here would just produce a harmless but confusing warning
# every build, also found by actually building the image.
RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app
COPY --from=build --chown=app:app /build/target/app.jar app.jar

USER app
EXPOSE 8080

# Container-aware JVM defaults (brief §66): -XX:MaxRAMPercentage lets the JVM size its
# heap from the container's actual memory limit (set via `docker run -m`/Compose
# `mem_limit`/the orchestrator's own resource limit) rather than a hard-coded -Xmx that
# would either waste memory or OOM-kill the container - no giant hard-coded value here.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Actuator health, not a shell-only "is the process alive" check - matches what the
# reverse proxy/orchestrator should also use for readiness (docs/operations/DEPLOYMENT.md).
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
    CMD curl -sf http://localhost:8080/actuator/health/readiness || exit 1

# Exec form (not shell form) so SIGTERM reaches the JVM directly for graceful shutdown
# (brief §67 - server.shutdown=graceful is set in application.yml) rather than being
# absorbed by an intermediate shell process.
ENTRYPOINT ["java", "-jar", "app.jar"]
