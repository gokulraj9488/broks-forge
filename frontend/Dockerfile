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
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
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
