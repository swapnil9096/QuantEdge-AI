# ===========================================================================
# QuantEdge AI — unified production Dockerfile.
#
# Stage 1: build the React/Vite frontend with Node.
# Stage 2: install Python deps, copy backend code + built frontend, run uvicorn.
#
# The single image serves:
#   - API routes at /api-style paths (e.g. /paper-portfolio, /deep-analyze/{sym})
#   - The React SPA at / (and any unknown path, for client-side refreshes)
# so the whole app lives on one port (8000) — no separate frontend server.
# ===========================================================================


# ---------- Stage 1: build the frontend ----------------------------------
FROM node:20-alpine AS frontend-builder
WORKDIR /frontend

# Layer-cache npm install separately from the source copy.
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci || npm install --no-audit --no-fund

COPY frontend/ ./
RUN npm run build


# ---------- Stage 2: runtime ---------------------------------------------
FROM python:3.12-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    APP_ENV=production \
    PORT=8000 \
    TZ=Asia/Kolkata

# System deps:
#   - curl/ca-certificates for HEALTHCHECK + outbound HTTPS to Yahoo/Portkey
#   - tzdata so the IST-aware market-status helper reports correct times
#   - tini as PID 1 for clean SIGTERM propagation
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        curl \
        ca-certificates \
        tini \
        tzdata \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install Python deps first so they cache independently of source changes.
COPY backend/requirements.txt /app/backend/requirements.txt
RUN pip install --upgrade pip \
    && pip install -r /app/backend/requirements.txt

# Backend source (includes secrets.enc.json if committed) and non-secret .env.
COPY backend/ /app/backend/

# Built React bundle from stage 1.
COPY --from=frontend-builder /frontend/dist /app/frontend/dist

# Persistent data dir (SQLite DB + any cached artefacts). The compose/fly
# volume is mounted over this path so nothing there survives the image; the
# image only ships an empty placeholder.
RUN mkdir -p /app/backend/data \
    && chmod 700 /app/backend/data

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://127.0.0.1:${PORT}/health || exit 1

WORKDIR /app/backend
ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["sh", "-c", "python -m uvicorn main:app --host 0.0.0.0 --port ${PORT}"]
