#
# conductor:server - Conductor Quarkus Server + ui-next
#
# Ports:
#   8080 - Conductor REST API, Swagger UI, Metrics, Health (Quarkus)
#   5000 - ui-next Web Dashboard (Nginx reverse proxy to 8080)
#
# Build args:
#   PREBUILT=true:  Uses pre-built Quarkus and UI artifacts
#   SKIP_UI=true:   Skips only the UI build
#   Default:        Builds Quarkus server and ui-next from source
#

# ===========================================================================================================
# 0. Server builder stage
# ===========================================================================================================
FROM azul/zulu-openjdk-debian:21 AS builder

ARG PREBUILT=false

LABEL maintainer="Orkes OSS <oss@orkes.io>"

COPY . /conductor
WORKDIR /conductor

RUN if [ "$PREBUILT" = "true" ] || [ -f "/conductor/server/build/quarkus-app/quarkus-run.jar" ]; then \
      echo "Using pre-built Quarkus application artifacts"; \
    else \
      ./gradlew :conductor-server:quarkusBuild -x test -x spotlessCheck \
        -Dorg.gradle.jvmargs=-Xmx2g \
        --no-daemon --no-parallel && \
      rm -rf /root/.gradle/caches; \
    fi

# ===========================================================================================================
# 1. UI builder stage (ui-next — pnpm + Vite + React 18)
# ===========================================================================================================
FROM node:22 AS ui-builder

ARG PREBUILT=false
ARG SKIP_UI=false

LABEL maintainer="Orkes OSS <oss@orkes.io>"

RUN corepack enable

COPY ui-next /conductor/ui-next
WORKDIR /conductor/ui-next

RUN if [ "$SKIP_UI" = "true" ]; then \
      mkdir -p dist && echo "UI build skipped (SKIP_UI=true)"; \
    elif [ "$PREBUILT" = "true" ] || [ -f "dist/index.html" ]; then \
      echo "Using pre-built ui-next/dist assets"; \
    else \
      pnpm install && NODE_OPTIONS=--max-old-space-size=4096 pnpm build && \
      pnpm store prune; \
    fi

# ===========================================================================================================
# 2. Final stage (Quarkus Server + Nginx for ui-next)
# ===========================================================================================================
FROM debian:stable-slim

LABEL maintainer="Orkes OSS <oss@orkes.io>"

RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-21-jre-headless nginx curl ca-certificates \
    && rm -f /etc/nginx/sites-enabled/default \
    && rm -rf /var/lib/apt/lists/*

# Create application directories
RUN mkdir -p /app/config /app/logs /app/data /app/bin

WORKDIR /app

# Copy configuration and startup script
COPY docker/server/config /app/config
COPY docker/server/bin/startup.sh /app/bin/startup.sh
RUN chmod +x /app/bin/startup.sh

# Copy compiled Quarkus application fast-jar layout
COPY --from=builder /conductor/server/build/quarkus-app/ /app/

# Copy ui-next build artifacts to Nginx web directory
WORKDIR /usr/share/nginx/html
RUN rm -rf ./*
COPY --from=ui-builder /conductor/ui-next/dist .
COPY docker/server/nginx/nginx.conf /etc/nginx/conf.d/default.conf

WORKDIR /app

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
EXPOSE 8080 5000

HEALTHCHECK --interval=30s --timeout=10s --retries=5 --start-period=15s \
    CMD curl -f http://localhost:8080/q/health || exit 1

ENTRYPOINT ["/app/bin/startup.sh"]
