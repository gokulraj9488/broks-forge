# AWS EC2 Production Setup — Brok's Forge

This is the one-time server setup for the permanent production host. After completing this
guide once, every future deployment is `git push origin main` — GitHub Actions
(`.github/workflows/deploy-production.yml`) does the rest. See
[DEPLOYMENT.md](./DEPLOYMENT.md) for the day-to-day deployment flow, environment variables,
and troubleshooting; this document is the initial server bring-up plus the security,
monitoring, and backup posture around it.

## Architecture

```
Vercel (frontend, Next.js)
        │  HTTPS
        ▼
broksforge.gokul.quest  (Vercel-managed domain)
        │
        ▼  HTTPS API calls
api.broksforge.gokul.quest
        │
        ▼
AWS EC2 (Amazon Linux 2023)
├── Nginx        (reverse proxy, TLS termination, ports 80/443)
├── Spring Boot  (backend container, no host port — reachable only via Nginx)
├── PostgreSQL   (container, no host port — reachable only from the backend)
├── Redis        (container, no host port — optional, see DEPLOYMENT.md)
└── Certbot      (container, renews the Let's Encrypt certificate automatically)
```

`gokul.quest` (the root domain, the portfolio) is untouched by any of this — only the
`broksforge` and `api.broksforge` subdomains are involved.

## 1. Provision the EC2 instance

- **AMI:** Amazon Linux 2023 (as stated in the task — already done).
- **Instance size:** `t3.small` (2 GiB RAM) is the practical minimum for Postgres + Redis +
  a JVM + Nginx running concurrently without swapping; `t3.micro` (1 GiB) will work but the
  JVM's `-XX:MaxRAMPercentage=75.0` (see `backend/Dockerfile`) leaves very little headroom for
  Postgres and the OS. Size up if you see OOM kills in `dmesg`.
- **Elastic IP:** allocate one and associate it with the instance. Without it, a stop/start
  cycle changes the public IP and silently breaks the `api.broksforge.gokul.quest` DNS `A`
  record until it's updated manually.
- **Security group** — this is the actual firewall (see [Security](#security) below for the
  full rationale):

  | Direction | Port | Source | Purpose |
  |---|---|---|---|
  | Inbound | 22 (SSH) | your admin IP(s) only — **not** `0.0.0.0/0` | Server administration |
  | Inbound | 80 (HTTP) | `0.0.0.0/0` | ACME HTTP-01 challenge + redirect to HTTPS |
  | Inbound | 443 (HTTPS) | `0.0.0.0/0` | The API |
  | Outbound | all | `0.0.0.0/0` | Default — needed for Docker Hub pulls, Let's Encrypt, provider API calls |

  Nothing else is opened. Postgres (5432) and Redis (6379) are **not** in this table at all —
  they're never exposed to the security group because `docker-compose.prod.yml` never
  publishes those ports to the host in the first place (see [Docker Compose](#docker-compose)).

## 2. Install Docker, Docker Compose, and Git

Already done per the task description, but for reference on a fresh Amazon Linux 2023 host:

```bash
sudo dnf update -y
sudo dnf install -y docker git
sudo systemctl enable --now docker

# Run Docker as a non-root deploy user (least privilege — see Security below)
sudo usermod -aG docker ec2-user
newgrp docker

# Docker Compose v2 ships as a Docker CLI plugin, not a separate binary, on
# recent Docker packages; verify it's present:
docker compose version
```

## 3. Clone the repository

```bash
sudo mkdir -p /opt/apps
sudo chown ec2-user:ec2-user /opt/apps
git clone https://github.com/gokulraj9488/broks-forge.git /opt/apps/broks-forge
cd /opt/apps/broks-forge
```

This path (`/opt/apps/broks-forge`) is what `.github/workflows/deploy-production.yml` assumes
by default (overridable via the `EC2_DEPLOY_PATH` GitHub Secret if you use a different path).

## 4. Configure the production environment

```bash
cp .env.production.example .env
nano .env   # fill in every value under "REQUIRED SECRETS"
```

Generate the two cryptographic secrets:

```bash
openssl rand -base64 48   # -> JWT_SECRET
openssl rand -base64 32   # -> ENCRYPTION_KEY
openssl rand -base64 24   # -> REDIS_PASSWORD
```

See [.env.production.example](../.env.production.example) for the full list, and
[DEPLOYMENT.md](./DEPLOYMENT.md) for what's required vs. optional and why.

## 5. Bootstrap the TLS certificate (one-time)

Before the first `docker compose -f docker-compose.prod.yml up -d`, the HTTPS server block in
`nginx/conf.d/broksforge-api.conf` points at a certificate that doesn't exist yet — Nginx would
refuse to start. Resolve this once with:

```bash
DOMAIN=api.broksforge.gokul.quest EMAIL=you@example.com ./nginx/init-letsencrypt.sh
```

This script (see its own header comment for the full explanation) issues a throwaway
self-signed cert so Nginx can start, brings Nginx up, requests the real certificate through
the now-running Nginx, then reloads it. Run it with `STAGING=1` first if you want to test
against Let's Encrypt's staging environment (much higher rate limits, but the browser will
show it as untrusted) before requesting a real one.

**Prerequisite:** the DNS `A` record for `api.broksforge.gokul.quest` must already point at
this instance's Elastic IP before running this script — Let's Encrypt's HTTP-01 challenge
validates ownership by connecting to the domain over the public internet. See
[DNS records](./DEPLOYMENT.md#dns-records) in DEPLOYMENT.md.

## 6. Start the stack

```bash
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

Confirm:

```bash
curl -s https://api.broksforge.gokul.quest/actuator/health
# {"status":"UP"}
```

## 7. Connect GitHub Actions (so every future deploy is `git push`)

Add the GitHub Secrets listed in [DEPLOYMENT.md → GitHub Secrets](./DEPLOYMENT.md#github-secrets)
— at minimum `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`. Generate a **dedicated** deploy keypair
(don't reuse your personal SSH key):

```bash
ssh-keygen -t ed25519 -f deploy_key -N "" -C "github-actions-deploy"
# Public half -> append to ~/.ssh/authorized_keys on the EC2 host (for the deploy user)
# Private half -> paste the FULL file contents into the EC2_SSH_KEY GitHub Secret
```

From then on, `git push origin main` triggers the workflow, which SSHes in and runs
`git pull && docker compose pull/build/up -d`. No further manual deployment steps.

## Security

- **SSH hardening:**
  - Key-only auth: in `/etc/ssh/sshd_config`, set `PasswordAuthentication no` and
    `PermitRootLogin no`, then `sudo systemctl reload sshd`.
  - Restrict the security group's port 22 rule to your admin IP(s) (or a VPN/bastion CIDR),
    never `0.0.0.0/0`.
  - Use a dedicated deploy keypair for GitHub Actions (above), so it can be rotated/revoked
    independently of any human's personal key.
- **Docker permissions / least privilege:** the deploy user (`ec2-user`) is in the `docker`
  group, not `root`, and doesn't need `sudo` for day-to-day deploys. Being in the `docker`
  group is root-equivalent on the host in practice (a container can bind-mount `/`), so it's
  still a privileged account — just don't grant it beyond what's needed, and don't add
  additional users to the `docker` group without the same consideration.
- **PostgreSQL / Redis not publicly exposed:** neither service has a `ports:` entry in
  `docker-compose.prod.yml` — they're reachable only from other containers on the
  `broksforge-net` Docker network. This is enforced at the Compose level, independent of and
  in addition to the security group never opening 5432/6379.
- **HTTPS only:** the HTTP (80) server block in `nginx/conf.d/broksforge-api.conf` does nothing
  but serve the ACME challenge and 301-redirect everything else to HTTPS; there's no
  plaintext API path.
- **Secure headers:** `Strict-Transport-Security`, `X-Content-Type-Options`, `X-Frame-Options`,
  `Referrer-Policy`, and `Permissions-Policy` are set at the Nginx layer (see
  `nginx/conf.d/broksforge-api.conf`); CSP is set by the backend itself
  (`SecurityConfig`) so it isn't duplicated/drifted across two layers.
- **Secret management:** application secrets live in `.env` on the EC2 host only (never in
  GitHub, never in the image) — see [.env.production.example](../.env.production.example) and
  [DEPLOYMENT.md → GitHub Secrets](./DEPLOYMENT.md#github-secrets) for exactly which secrets
  live where and why they're deliberately not duplicated.
- **OS patching:** Amazon Linux 2023 supports `dnf-automatic` for unattended security updates:
  `sudo dnf install -y dnf-automatic && sudo systemctl enable --now dnf-automatic.timer`.

## Monitoring & maintenance

- **Docker health checks:** every service in `docker-compose.prod.yml` has a `healthcheck:`;
  `docker compose -f docker-compose.prod.yml ps` shows `(healthy)`/`(unhealthy)` directly.
- **Restart strategy:** every service uses `restart: always` — a container that crashes (or an
  instance that reboots) comes back up on its own without manual intervention.
- **Log rotation:** every service sets `logging: {driver: json-file, options: {max-size: 10m,
  max-file: 5}}`, capping each container's logs at 50MB rather than growing unbounded. As a
  host-wide backstop (covers any container that doesn't set this explicitly, e.g. if you add
  one later), also set a Docker daemon default:
  ```bash
  sudo tee /etc/docker/daemon.json <<'EOF'
  { "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "5" } }
  EOF
  sudo systemctl restart docker
  ```
- **Disk usage guidance:** watch `/var/lib/docker` (images/containers/volumes) and the
  `postgres-data` volume specifically. `docker system df` gives a quick breakdown;
  `df -h /var/lib/docker` for the filesystem-level view. A `t3.small`'s default 20GB root
  volume is comfortable for a while but not indefinite — set a CloudWatch disk-usage alarm
  (or a simple cron `df` check + email) rather than discovering it's full during a deploy.
- **PostgreSQL backups:** `scripts/backup-postgres.sh`, scheduled via cron:
  ```bash
  crontab -e
  # 0 3 * * * cd /opt/apps/broks-forge && ./scripts/backup-postgres.sh >> /var/log/broksforge-backup.log 2>&1
  ```
  Dumps to `./backups/broksforge-<timestamp>.sql.gz`, prunes anything older than
  `RETENTION_DAYS` (default 14). See the script's header for the off-box-copy TODO (S3 etc.) —
  deliberately left unwired since it depends on infrastructure this guide doesn't assume.
- **Restore procedure:** `scripts/restore-postgres.sh <path-to-backup.sql.gz>` — destructive
  (drops and recreates the database), requires typing the database name to confirm. Stop the
  backend first (`docker compose -f docker-compose.prod.yml stop backend`) so nothing writes
  mid-restore, then start it again afterward.
- **Certificate renewal:** fully automatic — the `certbot` service in `docker-compose.prod.yml`
  runs `certbot renew` every 12 hours in a loop (a no-op unless the cert is within its ~30-day
  renewal window) and Nginx picks up a renewed cert on its next reload. No cron job needed;
  it's a long-running container, not a one-shot script.

## Future scaling recommendations

Not needed for V1 traffic levels, but worth knowing the path exists:

- **Vertical first:** resize the EC2 instance type before anything else — the stateless
  backend and the reverse proxy both benefit linearly from more CPU/RAM with zero
  architecture change.
- **Horizontal backend:** the backend is already stateless (JWT auth, no server-side
  sessions — see MASTER_ARCHITECTURE.md "Deployment Philosophy"). Scaling to multiple backend
  containers behind the same Nginx `upstream` block (`nginx/conf.d/broksforge-api.conf`) is a
  Compose `--scale backend=N` plus adding the extra `server backend:8080;` lines (or a
  `resolver`-based dynamic upstream) — no code change.
- **Managed database:** migrating Postgres from a container to Amazon RDS is a
  `SPRING_DATASOURCE_URL` change only (see `docker-compose.prod.yml`'s `backend` service) —
  worth doing once you need point-in-time recovery or read replicas beyond what the backup
  script above provides.
- **CDN/WAF in front of Nginx:** CloudFront or AWS WAF in front of the EC2 origin would add
  DDoS absorption and edge caching without touching the origin stack at all.
