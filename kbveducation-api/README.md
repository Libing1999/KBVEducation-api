# KBV Education — Backend (Phase 1)

Course Companion Platform API. Java 21 · Spring Boot 3.3 · PostgreSQL · JWT.

## Architecture

Layered architecture, package-by-layer under `com.kbv.education`:

```
controller   REST endpoints (thin; delegate to services)
service      Business logic — interface + impl (service/impl)
repository   Spring Data JPA repositories
entity       JPA entities (extend BaseEntity) + enums
dto          Request/response/auth DTOs (entities are never exposed)
mapper       MapStruct entity <-> DTO mappers
security     JWT, filters, principal, Spring Security wiring
config       Cross-cutting configuration (auditing, OpenAPI, CORS, security)
exception    Custom exceptions + global handler + error codes
utils        Constants and helpers
```

### Cross-cutting foundations (Step 1)
- `BaseEntity` — UUID PK, audit fields (`created_at/updated_at/created_by/updated_by`),
  soft-delete flag (`is_deleted`), optimistic-lock `version`. Auditing is wired via
  `JpaAuditingConfig` + `SecurityUtils`.
- `ApiResponse<T>` / `ApiError` / `PageResponse<T>` — uniform response envelope.
- `GlobalExceptionHandler` — maps exceptions to the envelope with correct HTTP status.
- `ErrorCode` — central error catalogue.

## Requirements
- JDK 21
- Maven 3.9+
- PostgreSQL 14+ (database `kbv_education`)

## Run
```bash
# configure DB + JWT via env or application.yml, then:
mvn spring-boot:run
```
Swagger UI: `http://localhost:8080/swagger-ui.html`

## Configuration (env overrides)
| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/kbv_education` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | DB credentials |
| `JWT_SECRET` | dev placeholder | Signing secret (>= 32 bytes) — **must** be overridden |
| `JWT_ACCESS_EXPIRATION` | `900000` | Access token TTL (ms) |
| `JWT_REFRESH_EXPIRATION` | `604800000` | Refresh token TTL (ms) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Allowed frontend origins |

## Docker

```bash
docker build -t kbv-education-api .
```

The image is a multi-stage build (Maven → JRE 21 runtime), installs
`postgresql-client` for the Backup module's `pg_dump` calls, runs as a
non-root user, and declares a `HEALTHCHECK` against `GET /actuator/health`.
For the full stack (Postgres + backend + frontend) see
[`../DEPLOYMENT.md`](../DEPLOYMENT.md) and [`../docker-compose.yml`](../docker-compose.yml).

## Build plan
- [x] **Step 1** — Backend project structure & foundations
- [x] **Step 2** — Frontend project structure
- [x] **Step 3** — Database schema (Flyway migrations + JPA entities)
- [x] **Step 4** — Authentication APIs (login / refresh / JWT / Spring Security)
- [x] **Step 5** — Admin APIs (users, students, parents, cohorts, dashboard)
- [x] Steps 6–8 — Frontend login, dashboards, CRUD pages
