# Deploying KBV Education to the DigitalOcean Droplet

> This repo is public — **never put passwords or `.env` contents in this file or in git.**

## At a glance

| What | Value |
|---|---|
| Server | `64.227.186.217` (Ubuntu 24.04, 1 CPU / 2 GB RAM + 2 GB swap) |
| SSH login | `ssh kbveducation@64.227.186.217` (password kept privately) |
| Site (HTTPS, recommended) | https://64-227-186-217.sslip.io |
| Site (plain HTTP) | http://64.227.186.217 — works, but voice recording is disabled by browsers on HTTP |
| Deployed branch | `main` of both GitHub repos |
| Deploy command | `~/apps/deploy.sh` |

## Normal deploy (after code changes)

1. **Push your changes to `main`** on GitHub — for either or both repos
   (`KBVEducation-api`, `KBVEducation-web`). The server deploys whatever is on `main`.

2. **Run the deploy script** — from PowerShell / terminal on your PC:

   ```bash
   ssh kbveducation@64.227.186.217 "~/apps/deploy.sh"
   ```

   Enter the SSH password when asked. The script:
   1. backs up the database to `~/backups/` (keeps the last 14),
   2. records the current commits in `~/apps/deploy-history.log` (for rollback),
   3. `git pull`s both repos,
   4. rebuilds the images and recreates the backend + frontend containers (the database keeps running),
   5. waits until the backend is healthy and prints `DEPLOY OK`.

   A full rebuild takes roughly 5–10 minutes on this droplet. The site stays up
   until the new containers replace the old ones (a few seconds of downtime).

3. **Check the site** at https://64-227-186-217.sslip.io.

Database schema changes are applied automatically by Flyway when the backend starts —
just add the new migration file under `src/main/resources/db/migration` and deploy.

## Server layout

```
/home/kbveducation/
  apps/
    kbv-education/              <- KBVEducation-api repo (docker-compose.yml lives here)
      .env                      <- production secrets (server only, chmod 600)
      docker-compose.override.yml  <- server only: passes ENCRYPTION_KEY, SMTP_*, AI keys to backend
      kbveducation-api/         <- Spring Boot source
    kbveducation-web/           <- KBVEducation-web repo (React source)
    deploy.sh                   <- deploy script (copy kept in this repo: scripts/deploy-droplet.sh)
    deploy-history.log          <- commit hashes of every deploy
  backups/                      <- db_YYYYMMDD_HHMMSS.sql.gz
```

Containers (Docker Compose project `kbv-education`):

| Service | What | Port |
|---|---|---|
| `frontend` | Caddy serving the React build, proxies `/api/*` to backend, automatic HTTPS | 80, 443 (public) |
| `backend` | Spring Boot API | 8099 → only on `127.0.0.1` |
| `db` | PostgreSQL 16 | internal only |

Data lives in Docker volumes (`db_data`, `storage_data` for uploads, `log_data`, `caddy_data`
for certificates) — they survive rebuilds and `docker compose down` (never add `-v`).

## Changing configuration (`.env`)

```bash
ssh kbveducation@64.227.186.217
cd ~/apps/kbv-education
nano .env                 # edit, Ctrl+O to save, Ctrl+X to exit
docker compose up -d      # recreates only containers whose config changed
```

Common edits:
- **Email (SMTP):** fill in `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_SENDER_EMAIL`
  (or set them in the app's admin Email Settings page instead).
- **AI:** `ANTHROPIC_API_KEY`, `GEMINI_API_KEY`.
- **Real domain later:** point the domain's DNS `A` record at `64.227.186.217`, then set
  ```
  SITE_ADDRESS=education.example.com, http://localhost
  CORS_ALLOWED_ORIGINS=https://education.example.com
  APPLICATION_URL=https://education.example.com
  ```
  and run `docker compose up -d`. Caddy gets the certificate automatically.
  Keep `http://localhost` in `SITE_ADDRESS` — the frontend container's health check calls it.

Do **not** change `JWT_SECRET` / `ENCRYPTION_KEY` casually: changing `ENCRYPTION_KEY` makes
stored SMTP passwords unreadable; changing `JWT_SECRET` logs everyone out.
`ADMIN_PASSWORD` is only used on the very first boot — change the admin password in the app.

## Useful commands (run in `~/apps/kbv-education`)

```bash
docker compose ps                         # status / health of all containers
docker compose logs -f backend            # follow backend logs (Ctrl+C to stop)
docker compose logs -f frontend           # Caddy / HTTPS logs
docker compose restart backend            # restart one service
cat ~/apps/deploy-history.log             # what was deployed when
ls -lh ~/backups                          # database backups
free -h; df -h /                          # memory / disk
```

## Rollback (if a deploy breaks the site)

```bash
cd ~/apps/kbv-education
tail -5 ~/apps/deploy-history.log         # find the last good api=... web=... hashes

git -C ~/apps/kbv-education    checkout <good-api-hash>
git -C ~/apps/kbveducation-web checkout <good-web-hash>
docker compose build
docker compose up -d --force-recreate --no-deps backend frontend
```

(`--force-recreate` matters: plain `docker compose up --build -d` can leave the old
containers running on the old images.)

If the bad deploy ran a database migration, also restore the backup taken just before it:

```bash
docker compose stop backend
gunzip -c ~/backups/db_YYYYMMDD_HHMMSS.sql.gz | \
  docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE $POSTGRES_DB WITH (FORCE);" -c "CREATE DATABASE $POSTGRES_DB;" && psql -U "$POSTGRES_USER" "$POSTGRES_DB"'
docker compose start backend
```

When the fix is pushed to `main`, return both repos to the branch and deploy normally:

```bash
git -C ~/apps/kbv-education checkout main
git -C ~/apps/kbveducation-web checkout main
~/apps/deploy.sh
```

## Manual backup / restore

```bash
cd ~/apps/kbv-education
# backup
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip > ~/backups/manual_$(date +%F).sql.gz
# copy a backup to your PC (run on your PC)
scp kbveducation@64.227.186.217:~/backups/<file>.sql.gz .
```

## Troubleshooting

| Symptom | Check |
|---|---|
| `deploy.sh` says backend not healthy | `docker compose logs --tail=200 backend` — usually a failed Flyway migration or bad `.env` value |
| `git pull` fails ("not possible to fast-forward") | Someone edited files on the server. `git -C <repo> status`, then `git -C <repo> reset --hard origin/main` (discards server edits) |
| HTTPS certificate error | `docker compose logs frontend` — ports 80/443 must be open (`sudo ufw status`) |
| Build killed / out of memory | `free -h` — swap must show 2 GB (`swapon --show`) |
| Disk full | `docker system prune -af` (removes unused images/build cache, not data volumes) |

## How it was set up (one-time, already done)

1. Gave `kbveducation` sudo; installed Docker Engine + Compose plugin from Docker's apt repo;
   added `kbveducation` to the `docker` group.
2. Added a 2 GB swap file (`/swapfile`, in `/etc/fstab`).
3. Firewall (`ufw`): only SSH, 80, 443 allowed.
4. Cloned both repos into `~/apps` (web repo cloned as `kbveducation-web`, which
   `docker-compose.yml` expects at `../kbveducation-web`).
5. Created `.env` with freshly generated secrets and `docker-compose.override.yml`.
6. `docker compose up --build -d`, then installed `~/apps/deploy.sh`. Containers have `restart: unless-stopped`, so they
   come back automatically after a droplet reboot.
