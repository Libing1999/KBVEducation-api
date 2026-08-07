# Deployment Guide

Docker-based deployment for the KBV Education platform: Postgres + the
Spring Boot API (`kbveducation-api`) + the React frontend (`kbveducation-web`),
wired together by [`docker-compose.yml`](./docker-compose.yml).

## Prerequisites

- Docker + Docker Compose v2 (`docker compose`, not the standalone `docker-compose`)
- The two repos checked out as **sibling directories** — `docker-compose.yml`
  builds the frontend from `../kbveducation-web`, so this layout is required:

  ```
  some-parent-dir/
    kbv-education/          <- this repo (contains docker-compose.yml)
      kbveducation-api/
    kbveducation-web/       <- sibling repo
  ```

## First run

```bash
cd kbv-education
cp kbveducation-api/.env.example .env
```

Edit `.env` and set at minimum:
- `JWT_SECRET` — a real random secret (`openssl rand -base64 48`). The
  container refuses to start without one (`JWT_SECRET must be set`).
- `ADMIN_PASSWORD` — the password for the seeded `SUPER_ADMIN` account
  (`ADMIN_EMAIL`, default `admin@kbv.edu`). Also required to start.
- `CORS_ALLOWED_ORIGINS` — the public origin the frontend will be served
  from, if not `http://localhost`.
- `SITE_ADDRESS` — the public domain (e.g. `education.kbv.com`). Enables
  HTTPS, which some features require outright — see
  [HTTPS is required](#https-is-required) below.

Then:

```bash
docker compose up --build
```

This starts three containers:

| Service | Image | Exposed on host |
|---|---|---|
| `db` | `postgres:16-alpine` | not published (internal only) |
| `backend` | built from `kbveducation-api/Dockerfile` | `${API_PORT:-8099}` → container `8080` |
| `frontend` | built from `../kbveducation-web/Dockerfile` | `${WEB_PORT:-80}` → container `80` |

Flyway migrations run automatically on backend startup, same as local dev.
The `SUPER_ADMIN` account is seeded on first boot if no admin user exists yet
(see `DataInitializer`).

Open `http://localhost` (or `$WEB_PORT`) and log in with the admin
credentials from `.env`.

## HTTPS is required

Set `SITE_ADDRESS` to the public hostname in `.env`:

```bash
SITE_ADDRESS=education.kbv.com
CORS_ALLOWED_ORIGINS=https://education.kbv.com
APPLICATION_URL=https://education.kbv.com
```

Caddy then provisions a Let's Encrypt certificate on first boot and redirects
`http://` → `https://` automatically. Three things this needs:

- `SITE_ADDRESS` must be a **hostname, not a bare IP** — Let's Encrypt does
  not issue certificates for IP addresses, and Caddy will fail issuance on
  boot if you give it one.
- The hostname's DNS `A`/`AAAA` record must already point at the host.
- Host port **80 must map to container port 80** (the default `WEB_PORT`) —
  Let's Encrypt's HTTP-01 challenge is answered there. If you override
  `WEB_PORT` to something else, certificate issuance fails.

### No domain yet?

`sslip.io` is a public wildcard DNS service that resolves any hostname with
an IP encoded in it back to that IP, and Let's Encrypt will certify those
names. So a host at `143.110.184.200` can get a real, fully-trusted
certificate with no domain purchase and no DNS configuration:

```bash
SITE_ADDRESS=143-110-184-200.sslip.io
CORS_ALLOWED_ORIGINS=https://143-110-184-200.sslip.io
APPLICATION_URL=https://143-110-184-200.sslip.io
```

The URL is ugly, but the certificate is genuine and every secure-context
feature works. Swap in a real domain later by changing these three values.

Serving over plain HTTP is not just a hardening gap — it disables features.
Browsers restrict a set of "powerful" APIs to **secure contexts**, and an
`http://` origin is not one. `localhost` is specifically exempt, which is why
these work in local dev and then silently fail on an `http://` deployment:

| Feature | API | Behaviour on plain HTTP |
|---|---|---|
| Reflection voice recording | `navigator.mediaDevices.getUserMedia` | `navigator.mediaDevices` is `undefined`; the recorder reports the page is not on HTTPS and falls back to file upload |

Also set `CORS_ALLOWED_ORIGINS` to the `https://` origin once HTTPS is on,
otherwise API calls from the new origin are rejected.

## How the pieces talk to each other

- The **frontend** is a static build served by Caddy (see `Caddyfile` in
  `kbveducation-web`). It never calls the backend's container hostname
  directly — the browser only ever talks to the frontend's own origin. Caddy
  reverse-proxies `/api/*` to `http://backend:8080` over the Docker network,
  mirroring the `/api` proxy `vite.config.ts` uses in local dev.
- The **backend** talks to Postgres via `DB_URL=jdbc:postgresql://db:5432/...`
  (the `db` hostname resolves via Docker's internal DNS).
- Health: `backend`'s image declares a `HEALTHCHECK` against
  `GET /actuator/health` (whitelisted publicly since Phase 1, now actually
  backed by `spring-boot-starter-actuator` as of Phase 5 Step 7). Check
  container health with `docker compose ps`.

## Data persistence

Three named volumes, so `docker compose down` (without `-v`) keeps everything:

| Volume | Contents |
|---|---|
| `db_data` | Postgres data directory |
| `storage_data` | Uploaded files (lesson attachments, homework submissions, certificates, backups) — `StorageProperties.basePath` inside the container is `/data/storage` |
| `log_data` | Rolling application log files (`logback-spring.xml`, non-`dev` profile) |

## Database backups in the container

The Backup module (Phase 5 Step 6) shells out to `pg_dump`. The backend
image installs `postgresql-client` specifically so this works the same way
it does on a local dev machine — without it, backups would fail silently
inside the container despite working outside one. `pg_dump` connects to the
`db` service over the same Docker network the app already uses.

## Updating a running deployment

```bash
git pull   # in both kbv-education and kbveducation-web
docker compose up --build -d
```

Flyway only ever applies forward migrations — there's no down-migration
step, matching how this project has always shipped schema changes.

## Logs

- `docker compose logs -f backend` — combined stdout (same as the console
  appender).
- The `log_data` volume additionally holds size-and-time-rotated log files
  (50 MB per file, 30 days / 1 GB retention) if you need something to `tail`
  outside of `docker logs`.

## Running without Docker

Each repo's own README documents running directly (`mvn spring-boot:run` /
`npm run dev`) for local development — Docker is the recommended path for
anything beyond a laptop, but is not required.
