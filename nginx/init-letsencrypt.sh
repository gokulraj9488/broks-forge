#!/usr/bin/env bash
# ===========================================================================
# Brok's Forge - First-time Let's Encrypt certificate bootstrap
# ---------------------------------------------------------------------------
# Run this ONCE on the EC2 host, from the repo root, before the very first
# `docker compose -f docker-compose.prod.yml up -d`:
#
#   DOMAIN=api.broksforge.gokul.quest EMAIL=you@example.com ./nginx/init-letsencrypt.sh
#
# Why this exists (the chicken-and-egg problem): the HTTPS server block in
# nginx/conf.d/broksforge-api.conf points at a certificate that doesn't exist
# yet, so Nginx would refuse to start at all; but Certbot's HTTP-01 challenge
# needs Nginx already running on port 80 to serve the challenge file. This
# script breaks the cycle: it generates a throwaway self-signed cert so Nginx
# can start, brings Nginx up, requests the REAL certificate via the running
# Nginx, then reloads Nginx so it picks up the real one. After this script
# succeeds once, the `certbot` service in docker-compose.prod.yml keeps the
# certificate renewed automatically — you never run this script again unless
# you add a new domain.
# ===========================================================================
set -euo pipefail

DOMAIN="${DOMAIN:?Set DOMAIN, e.g. DOMAIN=api.broksforge.gokul.quest}"
EMAIL="${EMAIL:?Set EMAIL, e.g. EMAIL=you@example.com}"
STAGING="${STAGING:-0}"  # Set STAGING=1 first to test against Let's Encrypt's staging rate limits.

COMPOSE="docker compose -f docker-compose.prod.yml"
CERTBOT_CONF="./nginx/certbot/conf"
CERTBOT_WWW="./nginx/certbot/www"
LIVE_PATH="$CERTBOT_CONF/live/$DOMAIN"

echo "==> Preparing directories"
mkdir -p "$CERTBOT_CONF" "$CERTBOT_WWW"

if [ -d "$LIVE_PATH" ]; then
  echo "Certificate for $DOMAIN already exists at $LIVE_PATH — nothing to do."
  echo "(Delete that directory first if you intend to re-bootstrap.)"
  exit 0
fi

echo "==> Creating a temporary self-signed certificate so Nginx can start"
mkdir -p "$LIVE_PATH"
openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
  -keyout "$LIVE_PATH/privkey.pem" \
  -out "$LIVE_PATH/fullchain.pem" \
  -subj "/CN=$DOMAIN"
cp "$LIVE_PATH/fullchain.pem" "$LIVE_PATH/chain.pem"

echo "==> Starting Nginx with the temporary certificate"
$COMPOSE up -d nginx

echo "==> Removing the temporary certificate so Certbot issues a fresh one"
rm -rf "$CERTBOT_CONF/live/$DOMAIN" "$CERTBOT_CONF/archive/$DOMAIN" "$CERTBOT_CONF/renewal/$DOMAIN.conf"

STAGING_ARG=""
if [ "$STAGING" = "1" ]; then
  STAGING_ARG="--staging"
  echo "==> STAGING mode: requesting a staging (non-trusted) certificate"
fi

echo "==> Requesting the real certificate from Let's Encrypt"
$COMPOSE run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    --email $EMAIL -d $DOMAIN \
    --rsa-key-size 2048 --agree-tos --no-eff-email $STAGING_ARG" \
  certbot

echo "==> Reloading Nginx to pick up the real certificate"
$COMPOSE exec nginx nginx -s reload

echo "==> Done. $DOMAIN is now serving a $( [ "$STAGING" = "1" ] && echo "STAGING (untrusted)" || echo "production" ) certificate."
echo "    The 'certbot' service will keep it renewed automatically from now on."
