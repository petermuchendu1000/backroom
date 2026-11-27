FROM node:20-alpine AS builder

WORKDIR /app

# Paths here are relative to the BUILD CONTEXT, which docker-compose.yml sets to
# ./backend/rest-api — not to the repository root. These two lines previously read
# `COPY backend/rest-api/...`, which resolves to backend/rest-api/backend/rest-api
# inside the context and does not exist:
#
#   ERROR [rest-api builder 5/6] COPY backend/rest-api/ .
#   failed to compute cache key: "/backend/rest-api": not found
#
# CI never caught it because CI runs `npm ci` and `npm run build` directly and
# never invokes docker build. The ws-signaling Dockerfile beside this one had it
# right all along — `COPY go.mod ./` and `COPY . .`.

# Install ALL dependencies (including dev for building)
COPY package*.json ./
RUN npm ci

# Copy source and build. .dockerignore keeps node_modules and dist out, so this
# cannot clobber the layer npm ci just produced.
COPY . .
RUN npm run build

# --- Production stage ---
FROM node:20-alpine

WORKDIR /app

COPY --from=builder /app/package*.json ./
RUN npm ci --omit=dev

COPY --from=builder /app/dist ./dist

# Expose port
EXPOSE 8080

# Start
CMD ["node", "dist/main"]

