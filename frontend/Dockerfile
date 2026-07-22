# ===========================================================================
# Brok's Forge - Frontend Dockerfile (multi-stage, Next.js standalone)
# ===========================================================================

# --------------------------- Dependencies ----------------------------------
FROM node:26-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
# Reproducible install from the committed lockfile (fails if it is out of sync).
RUN npm ci

# --------------------------- Build -----------------------------------------
FROM node:26-alpine AS build
WORKDIR /app
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
# Idle-session timeout in minutes, baked at build time (0 disables).
ARG NEXT_PUBLIC_IDLE_TIMEOUT_MINUTES=30
ENV NEXT_PUBLIC_IDLE_TIMEOUT_MINUTES=$NEXT_PUBLIC_IDLE_TIMEOUT_MINUTES
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# Git does not track empty directories, so a `public/` with no committed files
# (as in this repo today) is silently absent from a fresh `git checkout` —
# `COPY . .` above then has nothing to copy it from. Guarantee it exists here
# so the runtime stage's COPY --from=build below never fails, whether public/
# is empty, missing, or later populated with real static assets.
RUN mkdir -p public
RUN npm run build

# --------------------------- Runtime ---------------------------------------
FROM node:26-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

RUN addgroup -S nodejs && adduser -S nextjs -G nodejs

COPY --from=build /app/public ./public
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000

CMD ["node", "server.js"]
