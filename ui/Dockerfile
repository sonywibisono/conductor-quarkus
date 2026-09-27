# --- Dependencies Stage ---
FROM harbor-pp.iconpln.co.id/library/conductor-ui-deps:shadowing AS deps

# --- Build Stage ---
FROM harbor-pp.iconpln.co.id/library/node:22-alpine-pnpm-11.18.0 AS builder

WORKDIR /app

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml* .npmrc* ./
COPY --from=deps /app/node_modules ./node_modules
COPY . .

RUN if [ ! -f public/context.js ]; then \
  cp public/context.js.example public/context.js; \
  fi

ARG VITE_WF_SERVER
ARG VITE_USE_BFF=true
ARG VITE_BFF_GATEWAY_URL=""

ENV VITE_WF_SERVER=${VITE_WF_SERVER}
ENV VITE_USE_BFF=${VITE_USE_BFF}
ENV VITE_BFF_GATEWAY_URL=${VITE_BFF_GATEWAY_URL}

ENV NODE_OPTIONS="--max-old-space-size=4096"

RUN pnpm run build

# --- Runtime Stage ---
FROM harbor-pp.iconpln.co.id/library/nginx:stable-alpine AS runner

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]