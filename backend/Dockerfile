# syntax=docker/dockerfile:1
#
# Duello Quiz backend image (owner: devops). Build context: backend/.
#   docker build -t duello-backend backend/
#   docker buildx build --platform linux/amd64,linux/arm64 backend/   # what CI does
#
# Multi-arch: the build stage always runs on the build machine's own platform
# ($BUILDPLATFORM) because its output (jars + start scripts) is platform independent;
# only the small runtime stage is assembled per target platform. The bundled libwebp
# of webp-imageio ships linux x86_64 and aarch64 libraries.

# Base images are pinned literally (not via ARG) so Dependabot can update them.

# ---- build: Gradle installDist ----------------------------------------------------
FROM --platform=$BUILDPLATFORM eclipse-temurin:21.0.12.1_1-jdk-noble AS build
WORKDIR /src
ENV GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.welcome=never"

# Gradle distribution, plugins and dependency metadata first: this layer is reused
# while only src/ changes. (The dependency report is not configuration-cache safe.)
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
RUN ./gradlew --no-daemon --no-configuration-cache --quiet \
        dependencies --configuration runtimeClasspath > /dev/null

COPY src/ src/
# Tests run in ci-backend.yml (they need Docker for Testcontainers); the image only builds.
RUN ./gradlew --no-daemon installDist

# ---- runtime: glibc JRE (NOT Alpine: the bundled libwebp needs glibc >= 2.27) ------
FROM eclipse-temurin:21.0.12.1_1-jre-noble

LABEL org.opencontainers.image.title="duello-quiz-backend" \
      org.opencontainers.image.source="https://github.com/sabitsadiqli/Duello-Quiz" \
      org.opencontainers.image.licenses="UNLICENSED"

# curl is used by the HEALTHCHECK (the Temurin image normally has it already).
# Non-root user with a fixed UID so host directories (secrets, backups) can be chowned to it.
RUN set -eux; \
    if ! command -v curl > /dev/null; then \
        apt-get update; \
        apt-get install -y --no-install-recommends curl; \
        rm -rf /var/lib/apt/lists/*; \
    fi; \
    groupadd --system --gid 10001 duello; \
    useradd --system --uid 10001 --gid duello --home-dir /app --shell /usr/sbin/nologin duello; \
    mkdir -p /var/lib/duello/media; \
    chown -R duello:duello /var/lib/duello; \
    chmod 1777 /tmp

WORKDIR /app
# Application files stay owned by root (read-only for the app user).
COPY --from=build /src/build/install/duello-backend/ /app/

# -Djava.awt.headless=true is also in the start script (applicationDefaultJvmArgs);
# java.io.tmpdir must be writable: libwebp extracts its .so there on first use.
ENV PORT=8080 \
    MEDIA_DIR=/var/lib/duello/media \
    JAVA_OPTS="-Djava.awt.headless=true -Djava.io.tmpdir=/tmp -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

USER 10001:10001
EXPOSE 8080

# /health answers 200 only when the database is reachable. start-period covers JVM
# start + Flyway migrations; during it Docker probes every 5 s (Docker Engine >= 25).
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --start-interval=5s --retries=3 \
    CMD curl -fsS -o /dev/null "http://127.0.0.1:${PORT}/health" || exit 1

# No arguments: HTTP server. `import <dir>`: content importer (backend/README.md).
ENTRYPOINT ["/app/bin/duello-backend"]
