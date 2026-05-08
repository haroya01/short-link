#!/usr/bin/env bash
# Bootstraps Let's Encrypt certificates for kurl.me (apex + www).
#
# nginx refuses to start without the cert files referenced in its config, and
# certbot can't issue without nginx serving the http-01 challenge — chicken
# and egg. We solve it the way the wmnnd/nginx-certbot pattern does: drop a
# throwaway self-signed cert at the expected paths so nginx can start, run
# certbot to replace it, then reload nginx.
#
# Run this once on a fresh host after DNS A records point here. The certbot
# service in docker-compose.yml handles renewal from then on.
set -euo pipefail

if [ ! -f .env ]; then
  echo "deploy/.env not found — copy .env.example and fill it in first." >&2
  exit 1
fi

# shellcheck disable=SC1091
. .env

DOMAINS=("${BACKEND_HOST}" "www.${BACKEND_HOST}")
EMAIL="${LETSENCRYPT_EMAIL:?Set LETSENCRYPT_EMAIL in .env}"
STAGING="${LETSENCRYPT_STAGING:-0}"
LIVE_PATH="/etc/letsencrypt/live/${BACKEND_HOST}"

echo "[1/5] Creating a throwaway self-signed cert so nginx can start..."
docker compose run --rm --entrypoint "" certbot sh -c "
  mkdir -p ${LIVE_PATH} &&
  openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
    -keyout '${LIVE_PATH}/privkey.pem' \
    -out '${LIVE_PATH}/fullchain.pem' \
    -subj '/CN=localhost'
"

echo "[2/5] Starting nginx..."
docker compose up -d --force-recreate nginx

echo "[3/5] Removing the self-signed cert..."
docker compose run --rm --entrypoint "" certbot sh -c "rm -rf /etc/letsencrypt/live /etc/letsencrypt/archive /etc/letsencrypt/renewal"

echo "[4/5] Requesting real certificate for: ${DOMAINS[*]}"
ARGS=(
  certonly --webroot
  -w /var/www/certbot
  --email "$EMAIL"
  --agree-tos
  --no-eff-email
  --force-renewal
  --rsa-key-size 4096
)
[ "$STAGING" = "1" ] && ARGS+=(--staging)
for d in "${DOMAINS[@]}"; do ARGS+=(-d "$d"); done

docker compose run --rm --entrypoint "certbot" certbot "${ARGS[@]}"

echo "[5/5] Reloading nginx with the new certificates..."
docker compose exec nginx nginx -s reload

echo "Done. Renewal runs automatically every 12h via the certbot service."
