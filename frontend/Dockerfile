# Production frontend image (Phase 11 brief §7/§8). Multi-stage: a Node build stage
# that never ships, and an nginx runtime stage serving only the compiled static output
# plus reverse-proxying /api and /actuator to the backend (§8/§9 - same-origin
# production topology, ARCHITECTURE.md §13). Never `ng serve` in production.
#
# Build (from the frontend/ directory):
#   docker build -t foreigner-warsaw-frontend:<version-or-sha> .
#
# The built image expects one runtime environment variable, BACKEND_UPSTREAM (default
# "backend:8080" - the Compose service name/port from docker-compose.prod.yml), used by
# nginx.conf.template via envsubst at container start - see that file's own comment.

# --- Build stage --------------------------------------------------------------------
FROM node:24-bookworm-slim AS build
WORKDIR /build

COPY package.json package-lock.json ./
# Reproducible install from the committed lockfile (brief §175/§176) - never `npm
# install`, which can silently drift from package-lock.json.
RUN npm ci

COPY . .
RUN npm run build

# --- Runtime stage -------------------------------------------------------------------
# nginxinc's own "unprivileged" variant (not the plain nginx:alpine image) - pre-built
# to run its master process as a non-root user and listen on an unprivileged port out
# of the box (brief §64's "non-root" requirement), rather than a hand-rolled `USER`/
# permission workaround on top of the image that assumes root.
FROM nginxinc/nginx-unprivileged:1.27-alpine AS runtime

# Angular's compiled output only - no node_modules, no TypeScript source, no dev
# tooling ships in this image at all (brief §7/§62).
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY security-headers.conf /etc/nginx/security-headers.conf

ENV BACKEND_UPSTREAM=backend:8080

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
    CMD wget -q -O /dev/null http://localhost:8080/healthz || exit 1

# nginx:1.27's own entrypoint already envsubst's every *.template file in
# /etc/nginx/templates/ into /etc/nginx/conf.d/ before starting nginx in the
# foreground - no custom entrypoint script needed.
