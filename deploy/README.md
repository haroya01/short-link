# kurl.md production deploy

Single-host Docker deploy for the backend (Spring Boot + MySQL + Redis + nginx + Let's Encrypt). Frontend is hosted separately on Vercel.

## Topology

```
                        ┌──────────────────────────┐
  kurl.md            ─→ │  EC2 (this machine)      │
  www.kurl.md           │  ┌─────────┐  ┌────────┐ │
                        │  │  nginx  │→ │  app   │ │
                        │  │ (TLS)   │  │  :8080 │ │
                        │  └─────────┘  └────────┘ │
                        │     ↓ certbot renew      │
                        │  mysql        redis      │
                        └──────────────────────────┘

  app.kurl.md        ─→ Vercel (Next.js)
                        env: NEXT_PUBLIC_API_BASE=https://kurl.md
```

## Prereqs

- Linux host (Ubuntu 22.04+) with Docker + docker-compose plugin
- Inbound 80/443 open in security group
- DNS:
  - `kurl.md` A record → host's elastic IP
  - `www.kurl.md` A record → host's elastic IP
  - `app.kurl.md` CNAME → Vercel's hostname (after Vercel setup)
- A MaxMind license key is optional — without it the bundled GeoLite2-City fallback is used (no ASN data)

## Backend deploy (one-time)

```bash
# On the host:
git clone https://github.com/haroya01/short-link.git
git clone https://github.com/haroya01/short-link-frontend.git  # only needed if you ever rebuild here

cd short-link/deploy
cp .env.example .env
# fill in .env with real secrets

# Generate the JWT keypair:
openssl genrsa -out /tmp/private.pem 2048
openssl rsa -in /tmp/private.pem -pubout -out /tmp/public.pem
# Paste the inner base64 (between BEGIN/END) into JWT_PRIVATE_KEY / JWT_PUBLIC_KEY in .env

# 2FA secret encryption key:
openssl rand -base64 32   # → TWOFA_AES_KEY

# Add LETSENCRYPT_EMAIL=you@example.com to .env (only used for cert issuance).

# First boot — issue Let's Encrypt cert and start everything:
./init-letsencrypt.sh

# Subsequent updates:
docker compose pull
docker compose up -d --build
```

## Frontend deploy (Vercel)

1. In Vercel: import the `short-link-frontend` repo
2. Add environment variables:
   - `NEXT_PUBLIC_API_BASE=https://kurl.md`
3. Add custom domain `app.kurl.md` (Vercel will tell you the exact CNAME target)
4. Deploy

The frontend will call the backend at `https://kurl.md/...` directly. The browser handles the cross-subdomain cookie and CORS — backend already allows `https://app.kurl.md` via `CORS_ALLOWED_ORIGINS`.

## OAuth

Update the Google OAuth client (Cloud Console → Credentials):
- Authorized JavaScript origins: `https://app.kurl.md`
- Authorized redirect URIs: `https://kurl.md/login/oauth2/code/google`

## Operational

```bash
# Logs
docker compose logs -f app
docker compose logs -f nginx

# Restart just the app (e.g. after a build)
docker compose up -d --build app

# Backup MySQL
docker compose exec mysql mysqldump -uroot -p${MYSQL_ROOT_PASSWORD} short_link \
  | gzip > backups/short_link-$(date +%F).sql.gz

# Force-renew TLS (rare)
docker compose run --rm --entrypoint "certbot" certbot renew --force-renewal
docker compose exec nginx nginx -s reload
```

## Resource sizing

The whole stack (mysql + redis + app + nginx) fits inside ~1 GB RAM at idle. Recommended minimum:

- **t3.micro** (1 vCPU, 1 GB) — works but tight; mysql + JVM both want headroom
- **t3.small** (2 vCPU, 2 GB) — comfortable, ~$15/mo
- **t4g.small** (arm64) — same perf, ~30% cheaper

Backups: MySQL volume is the only stateful thing that matters. Either snapshot the EBS volume nightly (cheap) or run the `mysqldump` cron above to S3.
