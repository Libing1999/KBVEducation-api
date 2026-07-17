# KBV Education — Feature & Test Reference Document

**Course Companion Platform — Phases 1 through 5**
Backend: Spring Boot 3.3.4 / Java 21 / PostgreSQL / JWT · Frontend: React 19 / TypeScript / Vite / TailwindCSS

This document is grounded in the actual implemented code as of Phase 5 (Production Readiness) — every table, endpoint, business rule, and permission below was read directly from the Flyway migrations, entities, controllers, service implementations, DTOs, and the frontend router, not inferred or guessed. Test cases are written for manual QA use: `Pass/Fail` and `Remarks` columns are intentionally left blank.

## How to use this document

Each module below follows the same structure: Business Purpose, User Roles, Features Included, Database Tables, APIs, UI Pages, Business Rules, Validation Rules, Edge Cases, Permissions, and Test Cases. Modules are grouped by the phase that introduced them, though later phases sometimes extend an earlier module's tables (noted inline where relevant).

## Table of Contents

**Phase 1 — Foundations**
1. [Authentication](#1-authentication)
2. [User Management](#2-user-management)
3. [Cohort Management](#3-cohort-management)
4. [Dashboards](#4-dashboards)

**Phase 2 — Content**
5. [Lessons](#5-lessons)
6. [Lesson Files](#6-lesson-files)
7. [Homework](#7-homework)
8. [Quizzes](#8-quizzes)

**Phase 3 — Daily Activity**
9. [Reflections](#9-reflections)
10. [Practice Sessions](#10-practice-sessions)
11. [Progress Tracking](#11-progress-tracking)
12. [Notifications](#12-notifications)

**Phase 4 — Scoring & Analytics**
13. [Scoring Engine](#13-scoring-engine)
14. [Tier Engine](#14-tier-engine)
15. [Leaderboard](#15-leaderboard)
16. [Analytics & Statistics](#16-analytics--statistics)
17. [Data Export (Phase 4 original)](#17-data-export-phase-4-original)

**Phase 5 — Production Readiness**
18. [Certificate Management](#18-certificate-management)
19. [Generalized Export Module](#19-generalized-export-module)
20. [Audit Trail](#20-audit-trail)
21. [System Settings](#21-system-settings)
22. [Backup & Restore](#22-backup--restore)
23. [Security Hardening](#23-security-hardening)
24. [Error Monitoring](#24-error-monitoring)
25. [Performance & Caching](#25-performance--caching)
26. [Global Search](#26-global-search)
27. [Deployment & Infrastructure](#27-deployment--infrastructure)
28. [Testing Infrastructure](#28-testing-infrastructure)

**Global conventions** (apply to every table unless noted): UUID primary key, plus `created_at`/`updated_at` (`TIMESTAMPTZ`), `created_by`/`updated_by` (UUID), `is_deleted` (soft delete), `version` (optimistic lock) — the shared `BaseEntity` audit block. Error codes map to HTTP status as: `VALIDATION_ERROR`/`BAD_REQUEST` → 400, `UNAUTHORIZED`/`INVALID_CREDENTIALS` → 401, `ACCOUNT_INACTIVE`/`ACCESS_DENIED` → 403, `RESOURCE_NOT_FOUND` → 404, `DUPLICATE_RESOURCE` → 409, `BUSINESS_RULE_VIOLATION` → 422, `ACCOUNT_LOCKED` → 423, `RATE_LIMITED` → 429, `MAINTENANCE_MODE` → 503.

---

## 1. Authentication

**Business Purpose:** JWT-based login/session lifecycle for a platform with no public self-registration — every account is provisioned by a SUPER_ADMIN. Issues short-lived access tokens + rotating refresh tokens, and protects against brute-force login.

**User Roles:** All three roles use the same endpoints identically — no role gating on `AuthController` itself.

**Features Included:**
- Email/password login issuing an access token (JWT, 15 min default) + opaque refresh token (7 days default)
- Refresh-token rotation (old token revoked, new one issued on every `/refresh` call)
- Logout revoking all of a user's refresh tokens
- "Get current user" (`/me`)
- Forgot-password stub (no email delivery; always returns success to prevent account enumeration)
- Account lockout after repeated failed logins, with a 15-minute fixed cooldown
- Login session recording (IP, user-agent, login time) into `user_sessions`

**Database Tables:**
- `refresh_tokens` — `user_id` (FK), `token_hash` (SHA-256, unique — the raw token is never persisted), `expires_at`, `revoked`.
- `user_sessions` — `user_id` (FK), `refresh_token_id` (FK), `ip_address`, `user_agent`, `login_at`, `last_activity_at`, `expires_at`, `active`.
- `users.failed_login_attempts`, `users.locked_until` (added Phase 5 Step 7) — the lockout counter and cooldown timestamp.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/login` | Authenticate, returns access + refresh token pair |
| POST | `/api/auth/refresh` | Exchange a refresh token for a new pair (rotates it) |
| POST | `/api/auth/logout` | Revoke the current user's refresh tokens |
| GET | `/api/auth/me` | Get the currently authenticated user |
| POST | `/api/auth/forgot-password` | Request a password reset (stub, no email sent) |

**UI Pages:** `LoginPage` (`/login`), `ForgotPasswordPage` (`/forgot-password`) — inside `AuthLayout`, publicly reachable.

**Business Rules:**
- A locked account (`locked_until` in the future) is rejected with `ACCOUNT_LOCKED` (423) *before* the password is even checked.
- Bad credentials increment `failed_login_attempts`; at `system_settings.max_login_attempts` (default 5, admin-configurable 1–20) the account locks for a fixed 15 minutes (not admin-configurable).
- Successful login resets the counter, clears the lock, and stamps `last_login_at`.
- The failed-attempt write runs in its own `REQUIRES_NEW` transaction (`LoginAttemptService`) specifically so it survives the enclosing `login()` method throwing an exception to report the failure — see [Security Hardening](#23-security-hardening) for the bug this fixed.
- Refresh tokens are single-use: `/refresh` always revokes the presented token and issues a new one (rotation).
- If the underlying user is deleted/inactive at refresh time, **all** their refresh tokens are revoked.

**Validation Rules:**
- `LoginRequest`: `email` `@NotBlank @Email`; `password` `@NotBlank`.
- `RefreshTokenRequest`: `refreshToken` `@NotBlank`.
- `ForgotPasswordRequest`: `email` `@NotBlank @Email`.

**Edge Cases:**
- `/forgot-password` always returns generic success regardless of whether the email exists (anti-enumeration) — sends nothing.
- `/logout` silently no-ops for a stale/deleted-user token rather than erroring.
- The 6th failed attempt onward returns `ACCOUNT_LOCKED`, not `INVALID_CREDENTIALS`, even with the correct password.
- A locked account combined with hitting the rate limiter (10 requests/60s on `/login`) — the rate limiter fires first since it runs earlier in the filter chain.

**Permissions:** No `@PreAuthorize` on `AuthController`. `/login`, `/refresh`, `/forgot-password` are public (`AppConstants.PUBLIC_ENDPOINTS`); `/logout`, `/me` require any authenticated role.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| AUTH-01 | Login | Valid credentials succeed | Active, unlocked account exists | POST `/api/auth/login` with correct email/password | 200, access+refresh tokens returned, `last_login_at` updated | | |
| AUTH-02 | Login | Wrong password | Active account exists | POST `/api/auth/login` with wrong password | 401 `INVALID_CREDENTIALS`, `failed_login_attempts` incremented | | |
| AUTH-03 | Lockout | 5th wrong attempt locks the account | 4 prior failed attempts | POST `/api/auth/login` with wrong password a 5th time, then a 6th | 6th attempt returns 423 `ACCOUNT_LOCKED` even with correct password | | |
| AUTH-04 | Lockout | Admin unlock clears the lock | Account is locked | Admin calls `PUT /api/admin/users/{id}/unlock`, then user logs in with correct password | Unlock returns `locked=false`; subsequent login succeeds | | |
| AUTH-05 | Refresh | Token rotation | Valid refresh token | POST `/api/auth/refresh` with the token, then reuse the same old token | First call succeeds with a new pair; reusing the old (now-revoked) token fails | | |
| AUTH-06 | Logout | Revokes sessions | Logged-in user with a valid refresh token | POST `/api/auth/logout`, then attempt `/api/auth/refresh` with the old token | Refresh fails — token revoked | | |
| AUTH-07 | Forgot password | Unknown email doesn't leak existence | — | POST `/api/auth/forgot-password` with a nonexistent email | 200 generic success message, identical to a real email | | |
| AUTH-08 | Rate limiting | 11th request in 60s to `/login` is throttled | — | POST `/api/auth/login` 11 times within 60s from the same IP | 11th response is 429 `RATE_LIMITED` | | |

---

## 2. User Management

**Business Purpose:** Central admin-only CRUD for all platform accounts, plus specialized Student/Parent surfaces layering cohort/linking concerns on top of the generic user record. No self-registration exists — admins provision every account.

**User Roles:**
- **SUPER_ADMIN** — full CRUD, activate/deactivate, reset password, unlock, soft-delete, cohort-assign, parent-link.
- **STUDENT** — view/edit only their own profile (`ProfileController`).
- **PARENT** — view-only their own profile; cannot self-edit (the `PUT` is restricted to STUDENT/SUPER_ADMIN).

**Features Included:**
- Generic user list/get/create/update/status-toggle/reset-password/soft-delete/unlock (any role).
- Student-specific list/get/create/update/soft-delete + cohort assignment/removal.
- Parent-specific list/get/create/update/soft-delete + student linking/unlinking.
- Self-service profile view/edit.
- Forced session invalidation on deactivation, password reset, and soft-delete.

**Database Tables:**
- `users` — `email` (unique among non-deleted, case-insensitive), `password_hash`, `first_name`, `last_name`, `phone`, `role_id` (FK), `status` (`ACTIVE`/`INACTIVE`), `last_login_at`, `failed_login_attempts`, `locked_until`.
- `roles` — `name` (unique: `SUPER_ADMIN`/`STUDENT`/`PARENT`), `description`.
- `student_cohort` — `student_id`, `cohort_id`, `assigned_at`, `active` — partial unique index enforces one active row per student.
- `parent_student` — `parent_id`, `student_id`, unique pair.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/users` | List users (paginated, filter role/status/search) |
| GET | `/api/admin/users/{id}` | Get a user |
| POST | `/api/admin/users` | Create a user (any role) |
| PUT | `/api/admin/users/{id}` | Update profile fields |
| PATCH | `/api/admin/users/{id}/status` | Activate/deactivate |
| POST | `/api/admin/users/{id}/reset-password` | Admin password reset |
| DELETE | `/api/admin/users/{id}` | Soft-delete |
| PUT | `/api/admin/users/{id}/unlock` | Clear account lockout |
| GET/POST/PUT/DELETE | `/api/admin/students...` | Student CRUD + `POST/DELETE .../cohort` |
| GET/POST/PUT/DELETE | `/api/admin/parents...` | Parent CRUD + `POST/DELETE .../student` |
| GET, PUT | `/api/profile` | Self view/edit |

**UI Pages:** `UsersPage` (`/admin/users`), `StudentsPage` (`/admin/students`), `ParentsPage` (`/admin/parents`), `ProfilePage` (`/profile`).

**Business Rules:**
- Email uniqueness is case-insensitive and only against non-deleted users — a soft-deleted user's email can be reused.
- Deactivating, resetting a password for, or soft-deleting a user all forcibly revoke every refresh token for that user.
- `unlock` resets `failed_login_attempts` to 0 and clears `locked_until`.
- **Student/Parent creation does not run `PasswordPolicyValidator`** — only direct `UserController.create`/`resetPassword` do. A known inconsistency: admin-created students/parents are only checked against the DTO's static `@Size(min=8)`, not the dynamic system-settings password policy.
- Soft-deleting a student also deactivates their active cohort assignment; soft-deleting a parent also soft-deletes the parent-student link.
- Fetching a "Student" whose role isn't actually STUDENT (or a "Parent" that isn't PARENT) returns `ResourceNotFoundException`, not a type-mismatch error — avoids leaking existence.

**Validation Rules:**
- `CreateUserRequest`: `email` `@NotBlank @Email @Size(max=255)`; `password` `@NotBlank @Size(min=8, max=100)`; `firstName`/`lastName` `@NotBlank @Size(max=100)`; `role` `@NotNull`.
- `UpdateStatusRequest.status` `@NotNull`; `ResetPasswordRequest.newPassword` `@NotBlank @Size(min=8, max=100)`.
- `LinkStudentRequest.studentId` `@NotNull`.

**Edge Cases:**
- A parent can only ever have one linked student at a time (code-enforced, not a DB constraint) — re-linking soft-deletes the prior link.
- Password policy is dynamic but **only enforced for direct admin user creation/reset**, not student/parent creation — a real gap worth a dedicated QA test.
- Looking up a soft-deleted user by id 404s even though the row still physically exists.

**Permissions:** `UserController`, `StudentController`, `ParentController` — class-level `hasRole('SUPER_ADMIN')`. `ProfileController` — `GET` open to any authenticated user, `PUT` `hasAnyRole('STUDENT','SUPER_ADMIN')` (parents excluded from self-edit).

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| USR-01 | Create | Duplicate email rejected | A user with `a@kbv.edu` exists | POST create with the same email (any casing) | 409 `DUPLICATE_RESOURCE` | | |
| USR-02 | Deactivate | Deactivation kills sessions | User is logged in (has a refresh token) | Admin sets status to `INACTIVE` | User's refresh token is revoked; their next `/refresh` fails | | |
| USR-03 | Unlock | Unlock clears lockout state | Account locked from 5 failed attempts | `PUT /api/admin/users/{id}/unlock` | Response shows `locked:false`, `failedLoginAttempts:0` | | |
| USR-04 | Password policy | Student creation bypasses dynamic policy | `system_settings.password_require_digit=true` | Create a student with a password containing no digit but ≥8 chars | Creation succeeds (only the static `@Size` check applies) — flag if this is unexpected to stakeholders | | |
| USR-05 | Parent link | Re-linking replaces the old link | Parent already linked to Student A | Link the same parent to Student B | Old link (A) is soft-deleted; parent now resolves to Student B everywhere | | |
| USR-06 | Profile | Parent cannot self-edit | Logged in as PARENT | `PUT /api/profile` with new name | 403 — parents are excluded from self-edit | | |

---

## 3. Cohort Management

**Business Purpose:** Models course "intakes" — a batch of students studying together over a date range toward an exam date. Cohorts scope lessons, homework, quizzes, and the leaderboard.

**User Roles:** SUPER_ADMIN only.

**Features Included:**
- Cohort CRUD with pagination/filter/search by status.
- Archive (soft-delete) a cohort.
- List students actively in a cohort.
- Assign/remove a student to/from a cohort.

**Database Tables:**
- `cohorts` — `name`, `description`, `start_date`, `end_date`, `exam_date`, `status` (`UPCOMING`/`ACTIVE`/`COMPLETED`/`ARCHIVED`), `max_students` — CHECK `end_date >= start_date`.
- `student_cohort` (shared with User Management) — partial unique index `uq_sc_one_active_cohort` on `(student_id) WHERE active=TRUE` is the DB-level guarantee of "one active cohort per student."

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/cohorts` | List (paginated, filter status/search) |
| GET | `/api/admin/cohorts/{id}` | Get one |
| POST | `/api/admin/cohorts` | Create |
| PUT | `/api/admin/cohorts/{id}` | Update |
| DELETE | `/api/admin/cohorts/{id}` | Archive (soft-delete) |
| GET | `/api/admin/cohorts/{id}/students` | List active members |
| POST | `/api/admin/cohorts/{id}/students/{studentId}` | Assign a student |
| DELETE | `/api/admin/cohorts/{id}/students/{studentId}` | Remove a student |

**UI Pages:** `CohortsPage` (`/admin/cohorts`).

**Business Rules:**
- A student may have at most one active cohort assignment — assigning a new one deactivates any prior active assignment first.
- Assigning to an already-archived cohort is rejected (`BusinessRuleException`, 422).
- `max_students > 0` enforces capacity; `0` means unlimited.
- Re-assigning a student to the cohort they're already in is a silent no-op.
- `end_date >= start_date` is validated in code in addition to the DB CHECK.
- Archiving a cohort deactivates every active member's assignment **and** sets `status=ARCHIVED` + soft-deletes the cohort in one step — there is no unarchive/restore.

**Validation Rules:**
- `CreateCohortRequest`: `name` `@NotBlank @Size(max=150)`; `startDate`/`endDate` `@NotNull`; `maxStudents` `@PositiveOrZero`.
- `UpdateCohortRequest`: same, plus `status` `@NotNull` (must be explicit on update).

**Edge Cases:**
- Asymmetric "remove from cohort" behavior: the student-side endpoint silently no-ops if there's no active assignment, while the cohort-side endpoint throws `ResourceNotFoundException` for the same underlying condition.
- `listStudents` only shows active members — removed students vanish from the list even though their historical row remains.
- Archiving a cohort doesn't cascade-delete or block on its lessons/homework/quizzes — those remain pointing at an archived cohort.

**Permissions:** Class-level `hasRole('SUPER_ADMIN')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| COH-01 | Assign | One active cohort per student | Student already active in Cohort A | Assign the student to Cohort B | Cohort A assignment deactivates; student is now only active in B | | |
| COH-02 | Assign | Capacity limit enforced | Cohort has `max_students=1`, already has 1 active member | Assign a second student | 422 `BusinessRuleException` "reached its maximum capacity" | | |
| COH-03 | Assign | Unlimited capacity | Cohort has `max_students=0` | Assign 5 students | All 5 succeed | | |
| COH-04 | Archive | Archive deactivates all members | Cohort has 3 active members | `DELETE /api/admin/cohorts/{id}` | All 3 memberships deactivated; cohort `status=ARCHIVED`, `is_deleted=true` | | |
| COH-05 | Remove | Asymmetric no-op vs 404 | Student has no active cohort | Call the student-side remove endpoint, then the cohort-side remove endpoint for the same (nonexistent) pairing | Student-side: silent success. Cohort-side: 404 `ResourceNotFoundException` | | |

---

## 4. Dashboards

**Business Purpose:** An aggregate operational dashboard for admins (platform health/counts) and a personal score dashboard for students/parents (composite score, tier, cohort info).

**User Roles:**
- **SUPER_ADMIN** — `AdminDashboardController` only.
- **STUDENT** — own data via `DashboardController`/`StudentScoreController`.
- **PARENT** — their linked student's data, read-only.

**Features Included:**
- Admin: total students/parents/cohorts, active/inactive cohort counts, today's logins, locked-account count, disk-space system-health flag, 5 most recent users and cohorts.
- Student/parent: composite score + 4 category percentages, current tier, cohort name/status.
- Composite score endpoint (works for a student or a parent's linked student).
- Progress statistics (monthly + course-total activity counts, streaks).
- Student's own score calculation history (paginated).

**Database Tables:** `student_scores` (see [Scoring Engine](#13-scoring-engine)); `users.locked_until` for the locked-accounts count; `user_sessions` for today's-logins.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/dashboard` | Admin aggregate metrics + recent activity |
| GET | `/api/dashboard/me` | Score dashboard (student, or parent's linked student) |
| GET | `/api/dashboard/composite` | Composite score only |
| GET | `/api/dashboard/statistics` | Progress statistics |
| GET | `/api/student/score` | My current composite score |
| GET | `/api/student/score/history` | My score history, paginated |

**UI Pages:** `DashboardPage` (`/dashboard`) — role-aware, renders `AdminDashboard`, `ScoreDashboard`, or `ParentDashboard`.

**Business Rules:**
- `inactiveCohorts = totalCohorts − activeCohorts` (derived, not separately counted).
- "System healthy" = free disk space ≥ 500 MB (hardcoded threshold, Phase 5 Step 7 addition).
- `/api/dashboard/me` dispatches by the caller's granted role; any role other than STUDENT/PARENT gets a 400, not a clean 403 (business logic, not method security).
- A parent with no linked student gets `BusinessRuleException` (422), never a silently empty dashboard.
- **`upcomingLessons`/`recentNotifications` on the score dashboard are hardcoded placeholder data**, not derived from real lesson/notification records — a known stub, not a bug.

**Validation Rules:** None — all endpoints are GET-only.

**Edge Cases:**
- `AdminDashboardController` recomputes everything live on every call (no caching), including a filesystem free-space check each request.
- No `@PreAuthorize` on `DashboardController` at all — access control is purely business-logic-driven (400 for the wrong role), which is inconsistent with every other controller's pattern.

**Permissions:** `AdminDashboardController` — `hasRole('SUPER_ADMIN')`. `DashboardController` — none (any authenticated user; role checked at runtime). `StudentScoreController` — `hasRole('STUDENT')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| DASH-01 | Admin dashboard | Locked accounts count reflects reality | 2 accounts currently locked | `GET /api/admin/dashboard` | `lockedAccounts: 2` | | |
| DASH-02 | Admin dashboard | System health flips on low disk | Simulate/verify with disk near the 500MB threshold | `GET /api/admin/dashboard` | `systemHealthy` reflects the actual free space vs. 500MB | | |
| DASH-03 | Student dashboard | Wrong role gets 400 | Logged in as SUPER_ADMIN | `GET /api/dashboard/me` | 400, not 403 | | |
| DASH-04 | Parent dashboard | No linked student | Parent account with no `parent_student` row | `GET /api/dashboard/me` | 422 `BusinessRuleException` | | |

---

## 5. Lessons

**Business Purpose:** The core curriculum content unit — an admin-authored lesson (with files, an optional quiz, and optional homework) that cohort students see once published.

**User Roles:** SUPER_ADMIN — full CRUD, publish/unpublish, duplicate, reorder. STUDENT/PARENT — read-only, published + own-cohort only.

**Features Included:**
- Lesson CRUD with pagination/filter (cohort, status, search), default sort `displayOrder asc`.
- Publish/unpublish (with a one-time "new lesson published" notification to cohort students).
- Duplicate a lesson as a new draft.
- Batch reorder via `display_order`.
- Student/parent "my lessons" listing (published, cohort-scoped) with quiz/homework completion flags.
- Student/parent lesson detail (files, quiz status, homework status).

**Database Tables:**
- `lessons` — `cohort_id` (FK), `lesson_number`, `title`, `summary`, `description`, `lesson_date`, `status` (`DRAFT`/`PUBLISHED`), `published_date`, `display_order`.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET/POST | `/api/admin/lessons` | List / create |
| GET/PUT/DELETE | `/api/admin/lessons/{id}` | Get / update / soft-delete |
| POST | `/api/admin/lessons/{id}/publish` \| `/unpublish` | Publish / unpublish |
| POST | `/api/admin/lessons/{id}/duplicate` | Duplicate as draft |
| PATCH | `/api/admin/lessons/reorder` | Batch reorder |
| GET | `/api/student/lessons` | My published lessons |
| GET | `/api/student/lessons/{id}` | Lesson detail |
| GET | `/api/student/lessons/{lessonId}/files/{fileId}/download` | Download a file |

**UI Pages:** `LessonsPage` (`/admin/lessons`), `LessonDetailsPage` (`/admin/lessons/:id`), `MyLessonsPage` (`/lessons`), `StudentLessonDetailPage` (`/lessons/:id`).

**Business Rules:**
- New lessons default to `DRAFT`; `display_order` auto-increments per cohort if omitted.
- Notification fires only on the transition **into** published (`wasPublished` check) — republishing an already-published lesson doesn't re-notify.
- Unpublishing clears `published_date` and reverts to `DRAFT`.
- **Duplicate only copies scalar fields** (title gets " (Copy)") — files, quiz, and homework are *not* duplicated.
- Reorder does **not** validate that all items belong to the same cohort.
- Student visibility requires: `PUBLISHED` + student has an active cohort assignment + that cohort matches the lesson's — otherwise 404 (never reveals existence).

**Validation Rules:**
- `CreateLessonRequest`: `cohortId` `@NotNull`; `lessonNumber` `@Min(1)`; `title` `@NotBlank @Size(max=200)`.
- `ReorderRequest`: `items` `@NotEmpty`, each `id @NotNull`.

**Edge Cases:**
- List defaults to `displayOrder asc`, inconsistent with most other admin lists' `createdAt desc` default.
- `hasQuiz`/`hasHomework` flags are computed via a per-lesson existence query on every list call — an N+1 pattern worth watching on large lesson lists.
- Duplicate silently omitting files/quiz/homework is easy to miss without reading the code.

**Permissions:** `LessonController` — `hasRole('SUPER_ADMIN')`. `StudentLessonController` — `hasAnyRole('STUDENT','PARENT')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| LES-01 | Publish | First publish notifies cohort | Draft lesson, cohort has 3 active students | Publish the lesson | 3 students receive a `NEW_LESSON_PUBLISHED` notification | | |
| LES-02 | Publish | Republish doesn't re-notify | Already-published lesson | Publish again (no-op transition) | No new notification sent | | |
| LES-03 | Visibility | Draft lesson hidden from students | Lesson is `DRAFT` | Student in the matching cohort requests the lesson | 404, not 403 | | |
| LES-04 | Visibility | Wrong-cohort student blocked | Published lesson in Cohort A; student is in Cohort B | Student requests the lesson | 404 | | |
| LES-05 | Duplicate | Duplicate omits associations | Lesson has a file, quiz, and homework | Duplicate it | New draft lesson has the title+" (Copy)" but zero files/quiz/homework | | |

---

## 6. Lesson Files

**Business Purpose:** Attachment/material management for lessons — admins upload, students/parents download.

**User Roles:** SUPER_ADMIN manages; STUDENT/PARENT download only (for lessons they can see).

**Features Included:** List a lesson's files; upload one or more (multipart, extension-checked, duplicate-name-checked); download; delete (soft DB row + hard filesystem delete).

**Database Tables:**
- `lesson_files` — `lesson_id` (FK), `file_name` (original), `stored_name` (unique on-disk name, never exposed), `file_type`, `file_size`, `uploaded_date`.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/lessons/{lessonId}/files` | List |
| POST | `/api/admin/lessons/{lessonId}/files` | Upload (multipart) |
| GET | `/api/admin/lessons/{lessonId}/files/{fileId}/download` | Download |
| DELETE | `/api/admin/lessons/{lessonId}/files/{fileId}` | Delete |

**UI Pages:** `LessonFilesManager` component embedded in `LessonDetailsPage` (`/admin/lessons/:id`) — no standalone route.

**Business Rules:**
- Extension must be in `system_settings.allowed_file_types` — no per-lesson override (unlike homework).
- Duplicate filenames within the same lesson are rejected.
- Delete is a genuine hard filesystem delete alongside the soft-deleted DB row — a "restore" is not truly possible after delete.
- No max-file-size check exists for lesson files (unlike homework submissions).

**Validation Rules:** No dedicated DTO — extension/duplicate checks are imperative in the service.

**Edge Cases:**
- Empty file array upload → `BadRequestException`.
- Student download re-verifies the file actually belongs to the requested lesson (prevents cross-lesson `fileId` guessing).
- Concurrent in-flight downloads at delete time aren't explicitly handled.

**Permissions:** `LessonFileController` — `hasRole('SUPER_ADMIN')`. Student/parent download via `StudentLessonController` — `hasAnyRole('STUDENT','PARENT')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| LF-01 | Upload | Disallowed extension rejected | `system_settings.allowed_file_types` doesn't include `.exe` | Upload a `.exe` file | 400, listing the allowed extensions | | |
| LF-02 | Upload | Duplicate filename rejected | Lesson already has `notes.pdf` | Upload another `notes.pdf` | 400 | | |
| LF-03 | Download | Cross-lesson file id blocked | File belongs to Lesson A | Student requests it via Lesson B's download path | 404 | | |
| LF-04 | Delete | File removed from disk | File exists on disk and in DB | Delete it, then attempt download | DB row soft-deleted; download 404s; file physically gone | | |

---

## 7. Homework

**Business Purpose:** Per-lesson homework configured by admins (instructions, due date, allowed types, size cap) and submitted once per student with optional multi-file attachments.

**User Roles:** SUPER_ADMIN configures + reviews all submissions. STUDENT submits once + views own. PARENT views the linked student's submissions read-only (cannot submit).

**Features Included:** Get/upsert/delete a lesson's homework config; student single-attempt multi-file submission; own/linked-student submission viewing + file download; admin paginated submissions list + file download.

**Database Tables:**
- `homework` — `lesson_id` (FK), `title`, `instructions`, `due_date`, `allowed_file_types` (CSV), `max_file_size_mb` — partial unique index enforces one active config per lesson.
- `homework_submissions` — `homework_id`, `student_id`, `note`, `submitted_at` — partial unique index enforces one submission per student per homework (DB-level backstop).
- `homework_submission_files` — `submission_id`, `file_name`, `stored_name` (unique), `file_type`, `file_size`.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET/PUT | `/api/admin/lessons/{lessonId}/homework` | Get / upsert config |
| DELETE | `/api/admin/homework/{homeworkId}` | Delete config |
| POST | `/api/student/homework/{lessonId}` | Submit (multipart, STUDENT only) |
| GET | `/api/student/homework/{lessonId}` \| `/api/student/homework` | Get one / list mine |
| GET | `/api/student/homework/files/{fileId}/download` | Download my file |
| GET | `/api/admin/homework` | List all submissions (paginated) |
| GET | `/api/admin/homework/files/{fileId}/download` | Download any file |

**UI Pages:** `HomeworkConfigCard` + `HomeworkSubmission` components embedded in `LessonDetailsPage`/`StudentLessonDetailPage` — **no standalone page consumes the admin submissions list or its download endpoint**, though the endpoints exist and are exercisable via Swagger UI.

**Business Rules:**
- Config is a strict upsert — structurally one active homework row per lesson.
- `allowedFileTypes` is normalized on save (trimmed, lowercased, deduped).
- Resubmission is blocked (`BusinessRuleException`, 422) — one attempt per student per homework.
- Every file is validated (extension + size) **before any file is stored**, so a single bad file aborts the whole submission with no partial writes.
- A homework's own `allowed_file_types`/`max_file_size_mb` fall back to the global system setting when unset.
- Successful submission fires an admin notification and an activity-log entry (feeds Practice/Homework % scoring).

**Validation Rules:**
- `HomeworkRequest`: `title` `@NotBlank @Size(max=200)`; `maxFileSizeMb` `@Positive` (nullable = use global default).
- Submission itself is a raw multipart form, validated imperatively (no DTO annotations).

**Edge Cases:**
- A blank per-homework `allowed_file_types` silently falls back to the global list, not "reject everything."
- The admin submissions list/download endpoints have no frontend page — a QA tester won't find an "all submissions" screen in the UI.
- The DB unique index is a real backstop against a race-condition duplicate submit, independent of the application-level check.

**Permissions:** `HomeworkController` — `hasRole('SUPER_ADMIN')`. `StudentHomeworkController` — class `hasAnyRole('STUDENT','PARENT')`, `submit` narrowed to `hasRole('STUDENT')`. `HomeworkSubmissionAdminController` — `hasRole('SUPER_ADMIN')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| HW-01 | Submit | Resubmission blocked | Student already submitted | Submit again for the same homework | 422 `BusinessRuleException` | | |
| HW-02 | Submit | Oversized file rejected, nothing stored | `max_file_size_mb=5`, uploading a 10MB file among 2 valid files | Submit all 3 | Entire submission rejected 400; no files stored, no submission row created | | |
| HW-03 | Submit | Parent cannot submit | Logged in as PARENT with a linked student | POST submit | 403 | | |
| HW-04 | View | Parent sees linked student's submission | Student has submitted | Parent GETs the submission | 200, correct data | | |
| HW-05 | Config | Per-homework type override respected | Homework's `allowed_file_types="pdf"` (global allows more) | Submit a `.docx` file | 400 — only `.pdf` accepted for this homework | | |

---

## 8. Quizzes

**Business Purpose:** Per-lesson MCQ/open-ended quiz builder for admins and a single-attempt quiz-taking flow for students, with automatic MCQ scoring.

**User Roles:** SUPER_ADMIN builds/previews/deletes/reorders and views all attempts. STUDENT takes once (correct answers withheld while taking). PARENT has **no quiz access at all**.

**Features Included:** Get/upsert/delete a lesson's quiz; add/update/delete/reorder questions (MCQ requires exactly 4 options, exactly 1 correct); student take/submit (once) + own attempt history; admin paginated attempts list.

**Database Tables:**
- `quizzes` — `lesson_id`, `title`, `description`, `duration_minutes`, `passing_marks` (unused — "future use"), `status` (`DRAFT`/`PUBLISHED`) — one quiz per lesson (partial unique index).
- `quiz_questions` — `quiz_id`, `question_text`, `question_type` (`MCQ`/`OPEN_ENDED`), `marks`, `display_order`.
- `quiz_options` — `question_id`, `option_text`, `is_correct`, `display_order`.
- `quiz_attempts` — `quiz_id`, `student_id`, `status` (`IN_PROGRESS`/`SUBMITTED`), `score`, `max_score`, `submitted_at` — one attempt per student per quiz (DB-enforced).
- `quiz_answers` — `attempt_id`, `question_id`, `selected_option_id` (nullable), `answer_text` (nullable), `is_correct` (nullable, MCQ only).

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET/PUT | `/api/admin/lessons/{lessonId}/quiz` | Get / upsert quiz |
| GET | `/api/admin/quizzes/{quizId}` \| `/preview` | Get (both functionally identical) |
| DELETE | `/api/admin/quizzes/{quizId}` | Delete (cascades questions+options) |
| POST/PUT/DELETE | `/api/admin/quizzes/{quizId}/questions...` | Add/update/delete a question |
| PATCH | `/api/admin/quizzes/{quizId}/questions/reorder` | Reorder |
| GET | `/api/student/quizzes/{quizId}` | Take (answers withheld) |
| POST | `/api/student/quizzes/{quizId}/submit` | Submit (once) |
| GET | `/api/student/quiz-attempts` | My attempts |
| GET | `/api/admin/quiz-attempts` | All attempts (paginated) |

**UI Pages:** `QuizBuilder` embedded in `LessonDetailsPage` (no standalone route). `TakeQuizPage` (`/quizzes/:quizId`, STUDENT only). **`GET /api/admin/quiz-attempts` has no frontend page.**

**Business Rules:**
- MCQ questions must have exactly 4 options with exactly 1 correct — service-layer only, **not** a DB CHECK constraint.
- Updating a question replaces its options wholesale (soft-delete all, re-add), not a diff.
- Deleting a quiz cascades soft-delete to every question and option.
- A quiz-publish notification (`QUIZ_AVAILABLE`) only fires if the *parent lesson is already published* — publishing a quiz on a draft lesson sends nothing, with no later retroactive trigger.
- One attempt per student per quiz — DB-backed, no retakes.
- Scoring: `max_score` sums MCQ question marks only; open-ended answers are recorded but never auto-graded.
- An answer referencing an option that doesn't belong to its question is rejected (`BusinessRuleException`); an answer referencing an unknown question id is silently ignored.

**Validation Rules:**
- `QuestionRequest`: `questionText` `@NotBlank`; `questionType` `@NotNull`; option count/correctness enforced in the service, not annotations.
- `SubmitQuizRequest.answers` `@NotNull @Valid`.

**Edge Cases:**
- Question `reorderQuestions` **does** validate that all items belong to the quiz (unlike Lesson reorder, which doesn't) — an intentional inconsistency between the two features worth a regression test.
- `GET .../quizzes/{quizId}` and `.../preview` are literally the same call — no separate answer-hiding preview mode for admins.
- The MCQ "4 options / 1 correct" rule is bypassable via direct DB writes since it's application-level only.

**Permissions:** `QuizController` — `hasRole('SUPER_ADMIN')`. `StudentQuizController` — `hasRole('STUDENT')` (parents excluded, unlike Lessons/Homework). `QuizAttemptController` — `hasRole('SUPER_ADMIN')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| QUIZ-01 | Build | MCQ with 3 options rejected | — | Add an MCQ question with only 3 options | 400 | | |
| QUIZ-02 | Build | MCQ with 2 correct options rejected | — | Add an MCQ question with 2 options marked correct | 400 | | |
| QUIZ-03 | Take | Correct answers withheld | Published quiz | Student GETs the quiz to take it | Response options do not include `isCorrect` | | |
| QUIZ-04 | Submit | Resubmission blocked | Student already submitted | Submit again | 422 | | |
| QUIZ-05 | Submit | Scoring is correct | Quiz with 2 MCQ questions (1 mark each), 1 open-ended | Submit with both MCQ answers correct + a text answer | `score=2`, `max_score=2` (open-ended excluded) | | |
| QUIZ-06 | Access | Parent blocked entirely | Logged in as PARENT | GET or attempt to take any quiz | 403 | | |
| QUIZ-07 | Publish | No notify if lesson is draft | Lesson is `DRAFT`, quiz set to `PUBLISHED` | Publish the quiz | No `QUIZ_AVAILABLE` notification sent | | |

---

## 9. Reflections

**Business Purpose:** Students complete a daily typed and/or voice reflection against admin-configured questions; admins review/edit/export them globally. Feeds the Reflection % score component.

**User Roles:** SUPER_ADMIN — full question-bank CRUD + review/edit/delete/export/listen to any reflection. STUDENT — submit/edit today's only, view own history. PARENT — no direct access.

**Features Included:** Admin question CRUD (create/update/enable-disable/reorder/delete) + reflection review/edit-text/delete/export/listen. Student: get today's questions + existing entry, submit (typed + optional audio), edit until midnight, list/view own history, play own audio.

**Database Tables:**
- `reflection_questions` — `question_text`, `display_order`, `enabled`.
- `reflection_entries` — `student_id`, `reflection_date`, `reflection_type` (`TYPED`/`VOICE`/`BOTH`, computed automatically), `audio_file_name`/`audio_stored_name`/`audio_file_type`/`audio_file_size`, `submitted_at`, `transcript` (nullable, forward-compat, never populated today) — unique `(student_id, reflection_date)`.
- `reflection_answers` — `reflection_entry_id`, `question_id`, `answer_text`.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET/POST/PUT/PATCH/DELETE | `/api/admin/reflection-questions...` | Question bank CRUD, enable toggle, reorder |
| GET | `/api/admin/reflections` | List (filter cohort/student/type/search) |
| GET/PUT/DELETE | `/api/admin/reflections/{id}` | Get / edit text / delete |
| GET | `/api/admin/reflections/{id}/audio` \| `/export` | Listen / export as `.txt` |
| GET | `/api/student/reflections/today` \| `/api/student/reflections` \| `/{id}` | Today's Qs+entry / list / get one |
| POST/PUT | `/api/student/reflections` \| `/{id}` | Submit (multipart) / edit today's |
| GET | `/api/student/reflections/{id}/audio` | Play my audio |

**UI Pages:** `ReflectionsPage` (`/reflections`), `AdminReflectionsPage` (`/admin/reflections`), `AdminReflectionDetailPage` (`/admin/reflections/:id`), `ReflectionQuestionsPage` (`/admin/reflection-questions`).

**Business Rules:**
- One reflection per student per day, DB-enforced.
- Editable only on the reflection's own calendar day; admin text edits have no such restriction.
- `reflection_type` is auto-computed from whether typed/audio content exists, never client-chosen.
- Editing replaces answers wholesale (soft-delete + re-insert), not a diff.
- Successful submission triggers `study_days.has_reflection=true`, score recalculation (`REFLECTION_CHANGE`), leaderboard regeneration, and an admin notification.

**Validation Rules:**
- `ReflectionQuestionRequest.questionText` `@NotBlank`.
- Audio (service-layer): allowed `mp3, wav, m4a, aac`; max 25MB; virus-scanned.

**Edge Cases:**
- Submitting twice same day → 422.
- Editing after midnight → blocked with `BusinessRuleException`.
- A student requesting another student's reflection → 404 (ownership check, not 403).
- The score/streak side-effect of a submission runs in `REQUIRES_NEW` and swallows its own exceptions — a scoring failure never rolls back a successful submission, but can also fail silently.

**Permissions:** All three controllers — `hasRole('SUPER_ADMIN')` / `hasRole('STUDENT')` respectively (class-level).

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| REFL-01 | Submit | Duplicate same-day submit blocked | Already submitted today | Submit again | 422 | | |
| REFL-02 | Edit | Edit blocked after midnight | Reflection dated yesterday | Attempt to edit it today | 422 `BusinessRuleException` | | |
| REFL-03 | Type | Reflection type auto-computed | Submit with only audio, no typed answers | Submit | `reflection_type=VOICE` | | |
| REFL-04 | Ownership | Student cannot view another's reflection | Reflection belongs to Student B | Student A requests it by id | 404 | | |
| REFL-05 | Admin | Question disable removes it from today's list | Question is enabled | Admin disables it | Students no longer see it in `/today`, but past answers referencing it remain intact | | |

---

## 10. Practice Sessions

**Business Purpose:** Students log daily study sessions (with optional attachments) that admins manually review/approve/reject, feeding the Practice % score component and the "Full Papers" tier gate.

**User Roles:** STUDENT logs/views own, requests re-review of rejected sessions. SUPER_ADMIN reviews/approves/rejects/edits any, manages the re-review queue. PARENT — no direct access.

**Features Included:** Student: log a session (subject, date, duration, type, notes, attachments); list/view own; download own attachments; request a re-review. Admin: filterable/searchable list; approve/reject with comment; edit core fields; download any attachment; re-review request queue (approve/reject).

**Database Tables:**
- `practice_sessions` — `student_id`, `study_date`, `subject`, `duration_minutes` (CHECK > 0), `study_type` (`PAST_PAPER`/`WEAKNESS_PRACTICE`/`GENERAL_PRACTICE`), `notes`, `status` (`PENDING_REVIEW`/`APPROVED`/`REJECTED`), `admin_comment`, `reviewed_by`, `reviewed_at`.
- `practice_files` — `practice_session_id`, `file_name`, `stored_name` (unique), `file_type`, `file_size`.
- `practice_review_requests` — `practice_session_id`, `student_id`, `reason`, `status` (`PENDING`/`APPROVED`/`REJECTED`), `admin_notes`, `resolved_by`, `resolved_at`.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/student/practice` | Log a session (multipart) |
| GET | `/api/student/practice` \| `/{id}` | List / get mine |
| GET | `/api/student/practice/files/{fileId}/download` | Download my attachment |
| POST | `/api/student/practice/{id}/review-request` | Request re-review |
| GET | `/api/admin/practice` | List (filterable) |
| GET/PUT | `/api/admin/practice/{id}` | Get / edit |
| PUT | `/api/admin/practice/{id}/approve` \| `/reject` | Decide |
| GET | `/api/admin/practice/review-requests` | List re-review queue |
| PUT | `/api/admin/practice/review-requests/{id}/approve` \| `/reject` | Resolve |
| GET | `/api/admin/practice/files/{fileId}/download` | Download any attachment |

**UI Pages:** `PracticePage` (`/practice`), `PracticeDetailPage` (`/practice/:id`), `AdminPracticePage` (`/admin/practice`), `AdminPracticeDetailPage` (`/admin/practice/:id`), `ReviewRequestsPage` (`/admin/review-requests`).

**Business Rules:**
- New sessions default to `PENDING_REVIEW` (no auto-approval implemented).
- Attachments validated (extension whitelist, ≤25MB, virus-scanned) before any storage.
- A re-review request requires the session to currently be `REJECTED`, and only one `PENDING` request may exist at a time.
- Approving a re-review request cascades: sets the session to `APPROVED`, stamps reviewer/time.
- Deciding a `PAST_PAPER` session's approve/reject best-effort recalculates the student's tier (affects the "Full Papers" gate) — failures here are logged, not propagated.
- Admin field edits (`adminUpdate`) do **not** trigger score/tier recalculation, unlike approve/reject.

**Validation Rules:**
- `AdminUpdatePracticeRequest`: `subject` `@NotBlank @Size(max=200)`; `durationMinutes` `@Positive`.

**Edge Cases:**
- Requesting review on a non-rejected session → 422.
- A second re-review while one is `PENDING` → 422.
- Tier recalculation failure after a decision is swallowed (logged only) — approval itself always succeeds regardless.
- A voided study day (see [Progress Tracking](#11-progress-tracking)) excludes that day from Practice % even if the underlying session stays `APPROVED`.

**Permissions:** `StudentPracticeController` — `hasRole('STUDENT')`. `AdminPracticeController` — `hasRole('SUPER_ADMIN')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| PRAC-01 | Log | New session defaults to pending | — | Student logs a session | `status=PENDING_REVIEW` | | |
| PRAC-02 | Re-review | Cannot request on a pending session | Session is `PENDING_REVIEW` | Request re-review | 422 | | |
| PRAC-03 | Re-review | Second request blocked while one pending | Session is `REJECTED`, one `PENDING` request exists | Request re-review again | 422 | | |
| PRAC-04 | Approve | Approving a full paper recalculates tier | `PAST_PAPER` session, `PENDING_REVIEW` | Admin approves it | Session `APPROVED`; student's tier recalculation is attempted (check `tier_history` for a new row) | | |
| PRAC-05 | Void | Voided day excludes practice from scoring | Approved session on a day later voided by admin | Void the study day, check Practice % | That day no longer counts toward the numerator/denominator | | |

---

## 11. Progress Tracking

**Business Purpose:** Rollup activity metrics + streaks, a chronological timeline, and a calendar view for students/parents; aggregate cards + per-student drill-down for admins; the admin "void a study day" control that excludes a day from scoring.

**User Roles:** STUDENT/PARENT — own/linked progress, timeline, calendar. SUPER_ADMIN — aggregate stats, any student's drill-down, void a day.

**Features Included:** Monthly + course-total progress metrics and streaks; paginated timeline; calendar of study days; admin activity aggregate cards; admin per-student progress/timeline/calendar; void a study day.

**Database Tables:**
- `study_days` — `student_id`, `study_date`, `has_reflection`/`has_practice`/`has_homework`/`has_quiz` (booleans), plus (Phase 4) `is_voided`, `voided_reason`, `voided_by`, `voided_at` — unique `(student_id, study_date)`.
- `activity_logs` — `student_id`, `activity_type`, `title`, `description`, `reference_type`, `reference_id`, `occurred_at`.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/dashboard/progress` \| `/activity` \| `/calendar` | My rollup / timeline / calendar |
| GET | `/api/admin/dashboard/statistics` | Admin activity cards |
| GET | `/api/admin/dashboard/students/{id}/progress` \| `/activity` \| `/calendar` | Per-student drill-down |
| PATCH | `/api/admin/study-days/{id}/void` | Void a study day (reason required) |

**UI Pages:** `TimelinePage` (`/activity`), `CalendarPage` (`/calendar`), `AdminStudentActivityPage` (`/admin/students/:id/activity`).

**Business Rules:**
- Student id resolution: STUDENT → self; PARENT → linked student (422 if none); any other role → error.
- Streak calculation anchors on *today*, falling back to *yesterday* if today has no activity yet — avoids showing a broken streak mid-day.
- `ActivityService.record()` is the single choke point writing `activity_logs`, upserting `study_days`, and triggering score recalculation + leaderboard regeneration — runs in its own `REQUIRES_NEW` transaction and swallows all exceptions.
- Voiding a day is one-way and idempotency-guarded — voiding an already-voided day throws 422.

**Validation Rules:** `VoidStudyDayRequest.reason` `@NotBlank`.

**Edge Cases:**
- A day with both `has_practice` and `has_reflection` true, when voided, is audit-logged only under the `PRACTICE` entity type (practice wins the branch check).
- Timeline/calendar pagination is silently clamped to `size=100` server-side, not rejected.
- A parent with no linked student hitting any `/api/dashboard/*` endpoint gets 422, not zeroed data.

**Permissions:** `ProgressController` — `hasAnyRole('STUDENT','PARENT')`. `AdminStatisticsController`/`StudyDayAdminController` — `hasRole('SUPER_ADMIN')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| PROG-01 | Streak | Mid-day streak doesn't show as broken | Student reflected every day through yesterday, hasn't acted yet today | View streak before submitting today | Streak still shows yesterday's count, not 0 | | |
| PROG-02 | Void | Cannot void twice | Day already voided | Void it again | 422 | | |
| PROG-03 | Void | Voiding requires a reason | — | PATCH void with an empty reason | 400 | | |
| PROG-04 | Access | Parent with no link | Parent has no `parent_student` row | GET `/api/dashboard/progress` | 422 | | |

---

## 12. Notifications

**Business Purpose:** In-app notification center so students/admins are informed of review outcomes and new submissions without email/push infrastructure.

**User Roles:** All roles identical — list/count/read/delete only their own. No admin-authoring endpoint; all notifications are system-generated.

**Features Included:** List (paginated, unread-only filter); unread count; mark one/all as read; delete (soft) one.

**Database Tables:**
- `notifications` — `recipient_id` (FK, `ON DELETE CASCADE`), `type`, `title`, `message` (`VARCHAR(1000)`), `is_read`, `reference_type`, `reference_id`.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/notifications` | List mine (paginated, unread filter) |
| GET | `/api/notifications/unread-count` | Count |
| PATCH | `/api/notifications/{id}/read` \| `/read-all` | Mark read |
| DELETE | `/api/notifications/{id}` | Soft-delete |

**UI Pages:** `NotificationsPage` (`/notifications`), plus `NotificationBell` dropdown in the Topbar (keyboard-navigable, `role="menu"`, added Phase 5 Step 4).

**Business Rules:**
- Message content is sanitized via `InputSanitizer.sanitize(message, 2000)` on write — note the DB column is `VARCHAR(1000)`, a real latent mismatch (see Edge Cases).
- `notifyAdmins()`/`notifyCohortStudents()` fan-out loops are best-effort — failures are logged only, never block the triggering action.

**Validation Rules:** None client-facing (output-only DTOs; no create endpoint exists).

**Edge Cases:**
- **Sanitizer allows up to 2000 chars while the DB column is `VARCHAR(1000)`** — a long generated message could violate the DB constraint at insert time. Worth a dedicated QA/regression test.
- Recipient hard-delete cascades and removes notifications entirely — inconsistent with the soft-delete convention used everywhere else.
- Reading/deleting another user's notification by id → 404 (ownership-scoped query, not a leaked 403).

**Permissions:** `NotificationController` has **no `@PreAuthorize`** — relies on the global "any authenticated user" rule, scoped to `principal.getId()` in every query.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| NOTIF-01 | Read | Mark one as read | Unread notification exists | PATCH `/read` | `is_read=true`; unread count decrements | | |
| NOTIF-02 | Ownership | Cannot touch another user's notification | Notification belongs to User B | User A attempts to mark/delete it | 404 | | |
| NOTIF-03 | Message length | Long generated message near 1000-2000 chars | Trigger a notification with a long body (e.g. a long reflection-submitted message) | Observe the write | Verify whether it succeeds or hits a DB length violation — regression test for the sanitizer/column mismatch | | |
| NOTIF-04 | Fan-out | Cohort-wide notification reaches all active members | Cohort has 5 active students | Trigger a cohort notification (e.g. lesson publish) | All 5 receive it; a deactivated 6th member does not | | |

---

## 13. Scoring Engine

**Business Purpose:** Computes each student's weighted composite score (0–100) from four component percentages (Practice, Reflection, Homework, Quiz), driven by admin-configurable weights, persisting every calculation as an append-only history row.

**User Roles:** SUPER_ADMIN — view/update the active `ScoreConfig`; view score-related audit log. STUDENT — own current score + history.

**Features Included:** Get/update score configuration (weights must total exactly 100%); score-related audit log (filterable); student current composite score; student score history (paginated).

**Database Tables:**
- `score_config` — single active row: `practice_weight`/`reflection_weight`/`homework_weight`/`quiz_weight` (`NUMERIC(5,2)`, default 60/20/10/10, CHECK sum=100.00); `practice_window_start`, `reflection_window_start`/`_end`, `total_reflection_days` (default 90), `total_homework_count` (default 10), `leaderboard_enabled`, `leaderboard_sort_by`, `dashboard_widgets_enabled`.
- `student_scores` — append-only history: `student_id`, `cohort_id`, 4 percentages + `composite_score` (all `NUMERIC(5,2)`, 0–100 CHECK), weight snapshot, `trigger_reason`, `is_current` (partial unique: one current row per student).
- `score_audit_logs` — `entity_type`, `entity_id`, `student_id`, `action`, `previous_value`, `new_value`, `reason`.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET/PUT | `/api/admin/score-config` | Get / update (weights must total 100%) |
| GET | `/api/admin/audit-logs` | Score-related audit log |
| GET | `/api/student/score` \| `/history` | My current score / history |

**UI Pages:** `ScoreConfigPage` (`/admin/score-config`), `AuditLogPage` (`/admin/audit-log`); composite score also shown on `DashboardPage`.

**Business Rules (exact formulas):**
- **Practice %** = approved practice days ÷ available days in `[practiceWindowStart, min(today, cohortEnd)]`, minus voided days from both sides.
- **Reflection %** = reflection days ÷ `(totalReflectionDays − voidedDaysInWindow)`, within `[reflectionWindowStart, reflectionWindowEnd]`.
- **Homework %** = submitted count ÷ `total_homework_count` (config-defined, not per-cohort assignment count).
- **Quiz %** = average of `(score/maxScore×100)` across `SUBMITTED` attempts only; attempts with null/0 `maxScore` are excluded entirely, not counted as 0.
- **Composite** = weighted sum ÷ 100, rounded half-up to 2 decimals, clamped `[0,100]`.
- Every recalculation appends a new row and flips the prior `is_current` off — nothing is ever updated in place.
- Recalculation always cascades into tier recalculation, synchronously, same transaction.
- A `CONFIG_CHANGE` weight update recalculates **every active cohort's every student** — a potentially heavy, fully synchronous operation.

**Validation Rules:** `UpdateScoreConfigRequest`: all 4 weights `@NotNull @DecimalMin("0") @DecimalMax("100")`.

**Edge Cases:**
- Weights summing to 99.99% or 100.01% → rejected, with the actual computed sum echoed in the error.
- Practice/Reflection % silently return 0 (not an error) when the window isn't configured yet.
- A student who left their cohort forces Practice % to 0 regardless of actual logged activity.
- Voided study days can *raise* a percentage if the voided days were previously misses.

**Permissions:** `AdminScoreConfigController`/`AdminScoreAuditLogController` — `hasRole('SUPER_ADMIN')`. `StudentScoreController` — `hasRole('STUDENT')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| SCORE-01 | Config | Weights must total 100% | — | PUT config with weights summing to 99 | 422, error message states the actual sum | | |
| SCORE-02 | Config | Valid update recalculates everyone | Multiple active cohorts with students | Update weights to a valid 100% split | Every student across every active cohort gets a new `student_scores` row with `trigger_reason=CONFIG_CHANGE` | | |
| SCORE-03 | Quiz % | Zero-question quiz excluded from average | Student has one `SUBMITTED` attempt with `maxScore=0` | Recalculate | That attempt doesn't drag Quiz % to 0 — it's excluded from both numerator and denominator | | |
| SCORE-04 | History | Every recalculation is append-only | Student has an existing current score | Trigger any recalculation | Old row's `is_current` flips to false; new row inserted with `is_current=true` | | |

---

## 14. Tier Engine

**Business Purpose:** Translates composite score + Practice % + approved full-papers count into a graduation tier using admin-configurable thresholds, with distinct system-calculated vs. admin-confirmed/overridden states and full history.

**User Roles:** SUPER_ADMIN — confirm/override any student's tier, view history, configure tier rules. STUDENT — own current tier + next tier + exactly what's missing.

**Features Included:** Confirm a calculated tier as-is; override to any active tier (reason mandatory); paginated tier decision history; list/bulk-update tier rule thresholds (overlap-validated); student view of calculated/confirmed tier, next tier, and remaining requirements.

**Database Tables:**
- `tier_rules` — `tier_name`, `tier_rank` (1=best), `min_composite`, `max_composite` (informational only, never gates matching), `min_practice_percentage`, `min_full_papers`, `active` — seeded: Tier 1 (rank 1, min 90, practice 88, papers 12), Tier 2 (rank 2, min 80, practice 83, papers 6), Tier 3 (rank 3, min 60, practice 71, papers 0), Not Passing (rank 4, min 0).
- `tier_history` — append-only: `student_id`, `calculated_tier`, `confirmed_tier` (nullable), `is_override`, `override_reason`, score/practice/papers snapshot, `decided_by`, `source` (`SYSTEM`/`ADMIN_CONFIRM`/`ADMIN_OVERRIDE`).

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| PUT | `/api/admin/tier/{studentId}/confirm` | Confirm calculated tier |
| PUT | `/api/admin/tier/{studentId}` | Override (`{tierName, reason}`) |
| GET | `/api/admin/tier/{studentId}/history` | Decision history |
| GET/PUT | `/api/admin/tier-rules` | List / bulk-update rules |
| GET | `/api/student/tier` | My tier + next tier + gaps |

**UI Pages:** `TierRulesPage` (`/admin/tier-rules`) — rule config only. Student progress via `TierProgressCard` on `DashboardPage`. **No dedicated frontend page for admin confirm/override or per-student history** — those endpoints exist but aren't wired into any admin UI page; flag to QA/PM as a possible gap.

**Business Rules:**
- Tier matching walks rules best-first and picks the first whose *minimum* thresholds are all satisfied — `max_composite` is never a gate, only informational, so a high-composite student who misses a lower tier's practice %/papers gate can land lower than composite alone suggests.
- "Next tier"/"remaining requirements" are computed relative to the **display tier** (confirmed/override if set, else calculated) — stays consistent after an override.
- Confirming/overriding copies forward the latest calculated snapshot onto a new history row, never mutates the calculated row.
- Override target must match an active tier rule name — arbitrary free text is rejected.
- Bulk tier-rule update validates no gap/overlap across `[min,max]` ranges for the whole batch.

**Validation Rules:** `OverrideTierRequest`: `tierName` `@NotBlank`; `reason` `@NotBlank` (mandatory, not optional). `UpsertTierRuleRequest`: `tierRank` `@Min(1)`; composite/practice fields `@DecimalMin/Max("0"/"100")`.

**Edge Cases:**
- Confirming/overriding a student with no calculated tier yet → 404.
- Overriding to an inactive/unknown tier name → 400.
- Overlapping thresholds reject the **entire batch**, not per-rule.
- No optimistic-locking guard for two admins overriding the same student simultaneously — last write wins.

**Permissions:** `AdminTierController`/`AdminTierRuleController` — `hasRole('SUPER_ADMIN')`. `StudentTierController` — `hasRole('STUDENT')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| TIER-01 | Match | Practice gate blocks a high-composite student | Student has 95% composite but only 50% practice (Tier 1 needs 88%) | Recalculate tier | Student lands below Tier 1 despite the high composite | | |
| TIER-02 | Override | Reason is mandatory | — | PUT override with an empty reason | 400 | | |
| TIER-03 | Override | Unknown tier name rejected | — | PUT override with `tierName="Gold"` (not configured) | 400 | | |
| TIER-04 | Rules | Overlapping thresholds rejected | — | Bulk-update rules so Tier 2's max overlaps Tier 1's min | 422, entire batch rejected | | |
| TIER-05 | Display | Next-tier gap reflects the override, not the raw calculation | Student's calculated tier is "Not Passing" but admin overrode to Tier 3 | GET `/api/student/tier` | "Next tier" and gaps are relative to Tier 3, not "Not Passing" | | |

---

## 15. Leaderboard

**Business Purpose:** Ranks students within their cohort by a chosen metric, cached as pre-computed snapshots per (cohort, sort field) for fast reads rather than computed live.

**User Roles:** SUPER_ADMIN — any cohort, manual regeneration. STUDENT — own cohort only, rankings/scores only (never other students' underlying activity detail).

**Features Included:** Admin paginated leaderboard for a cohort + sort field; manual regeneration. Student full leaderboard for own cohort, optionally re-sorted.

**Database Tables:**
- `leaderboard_snapshot` — `cohort_id`, `student_id`, `rank`, 4 percentages + `composite_score`, `current_tier`, `sort_by` (`COMPOSITE`/`PRACTICE`/`QUIZ`/`REFLECTION`/`HOMEWORK`), `generated_at`.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/leaderboard` | List a cohort's leaderboard |
| POST | `/api/admin/leaderboard/regenerate` | Manual regeneration |
| GET | `/api/student/leaderboard` | My cohort's leaderboard |

**UI Pages:** `AdminLeaderboardPage` (`/admin/leaderboard`), `LeaderboardPage` (`/leaderboard`).

**Business Rules:**
- Regeneration is a full rebuild per sort field: existing rows for that `(cohort, sortBy)` are **hard-deleted** (disposable cache, not history) then re-inserted from scratch.
- Regeneration always refreshes Analytics too, regardless of `leaderboard_enabled`.
- If `leaderboard_enabled=false`, regeneration writes no snapshot rows (but still refreshes analytics).
- Ranking ties have no documented tiebreaker beyond natural stream order.
- Students get an explicit error, never a silently empty leaderboard, if disabled or unassigned to a cohort.

**Validation Rules:** No request-body DTOs (query params only); `sortBy` binds to an enum.

**Edge Cases:**
- A student who leaves their cohort can leave a stale snapshot row until the next regeneration trigger for that cohort.
- Cohort-wide rebuild on every trigger (not debounced) — a burst of concurrent activity in one cohort causes repeated full rebuilds.
- `current_tier` on each row is a snapshot at regeneration time — can go stale relative to a later tier confirm/override until the next regeneration.

**Permissions:** `AdminLeaderboardController` — `hasRole('SUPER_ADMIN')`. `StudentLeaderboardController` — `hasRole('STUDENT')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| LB-01 | Regenerate | Full rebuild replaces stale rows | Cohort has an existing leaderboard | Change a student's score, then regenerate | Rank order updates to reflect the new score; no duplicate/stale rows remain | | |
| LB-02 | Disabled | Disabled leaderboard blocks students with a clear error | `leaderboard_enabled=false` | Student requests their leaderboard | 422, explicit "leaderboard is currently disabled" message, not an empty list | | |
| LB-03 | Scope | Student cannot see another cohort's leaderboard | Student is in Cohort A | Attempt to view Cohort B's leaderboard | Endpoint only ever returns the caller's own cohort — no cohortId param is accepted from the student side | | |
| LB-04 | No cohort | Unassigned student gets an explicit error | Student has no active cohort | GET `/api/student/leaderboard` | 422, "not assigned to a cohort" | | |

---

## 16. Analytics & Statistics

**Business Purpose:** Cached aggregate metrics (averages, tier distribution, activity counts) and time-series trends across a cohort or globally, so admins can spot at-risk students and track engagement without recomputing on every dashboard load.

**User Roles:** SUPER_ADMIN only.

**Features Included:** Aggregate analytics snapshot (global or per-cohort); daily average trend for one metric over a day window; per-student composite-score trend for a list of student ids; activity statistics cards (shared with Progress Tracking).

**Database Tables:**
- `dashboard_metrics` — `cohort_id` (nullable=global), `metric_key` (14 fixed keys: `AVG_COMPOSITE`, `HIGHEST_SCORE`, tier counts, activity counts, etc.), `metric_value`, `computed_at`.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/analytics` | Aggregate snapshot |
| GET | `/api/admin/analytics/trend` | Daily average trend for one metric |
| GET | `/api/admin/analytics/student-trend` | Per-student composite trend |
| GET | `/api/admin/dashboard` \| `/dashboard/statistics` | Admin dashboard metrics / activity cards |

**UI Pages:** `AdminAnalyticsPage` (`/admin/analytics`).

**Business Rules:**
- Metrics are lazily computed: cache-hit if `dashboard_metrics` rows exist, else a full recompute — served from cache until the next explicit refresh trigger (leaderboard regeneration or scheduler), no TTL-based auto-expiry.
- "Active students" = students with a current score row in scope, not "logged in recently."
- "At risk" = students in the worst-ranked tier via their **display** (confirmed/override-aware) tier.
- Tier distribution collapses any rank beyond 1/2/3 into `NOT_PASSING_COUNT` — a hypothetical 5th configured tier rank would also land there.
- Trend data buckets by the calendar day the recalculation happened, not a reconstructed value for every day (documented simplification).

**Validation Rules:** `days` has no min/max validation — negative/huge values are silently coerced to a minimum 1-day window, not rejected.

**Edge Cases:**
- A cohort with no current scores yet → all metrics default to 0, not an error.
- `student-trend` silently drops any unresolvable student id rather than erroring.
- Cache-first means a stale snapshot can be served until something triggers a refresh.

**Permissions:** All analytics/statistics controllers — `hasRole('SUPER_ADMIN')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| ANL-01 | Cache | Stale metrics served until refresh | Cached metrics exist from before a score change | Change a student's score, immediately GET analytics | Old cached values still shown until a refresh trigger fires | | |
| ANL-02 | At-risk | Overridden tier affects at-risk count | Student's calculated tier is "Not Passing", admin overrides to Tier 2 | Refresh analytics | Student no longer counted as at-risk | | |
| ANL-03 | Trend | Negative days param coerced | — | GET trend with `days=-5` | Coerced to a 1-day window, not rejected | | |
| ANL-04 | Student trend | Unknown student id silently skipped | One valid + one bogus student id in the list | GET student-trend with both | Response includes only the valid student's data | | |

---

## 17. Data Export (Phase 4 original)

**Business Purpose:** The original four CSV/Excel export endpoints (leaderboard, scores, tiers, student progress), predating the Phase 5 generalized export registry. Still live, surfaced as inline "CSV / Excel" buttons on other admin pages rather than a standalone export page.

**User Roles:** SUPER_ADMIN only.

**Features Included:** Export a cohort's leaderboard / current scores / current tier decisions as CSV or XLSX; export one student's progress as CSV or XLSX.

**Database Tables:** No dedicated content table (reads live from `leaderboard_snapshot`, `student_scores`, `tier_history`); every run is recorded to `export_history` (see [Generalized Export Module](#19-generalized-export-module)) under dataset values `LEADERBOARD`, `COMPOSITE_SCORES`, `TIER_HISTORY`, `PROGRESS`.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/export/leaderboard` | Export a cohort's leaderboard |
| GET | `/api/admin/export/scores` | Export a cohort's current scores |
| GET | `/api/admin/export/tiers` | Export a cohort's current tier decisions |
| GET | `/api/admin/export/progress/{studentId}` | Export one student's progress |

**UI Pages:** No standalone page — `ExportButtons` (CSV/Excel pair) embedded on `AdminLeaderboardPage`, `AdminAnalyticsPage` (scores + tiers), `AdminStudentActivityPage` (progress).

**Business Rules:**
- Every call is `@Audited(action="EXPORT_GENERATED")`.
- `exportTiers` only emits a row for cohort members who already have at least one tier decision — never-tiered students are silently omitted, not shown blank.
- `exportScores` only includes students with a **current** score row.
- Dataset classification for `export_history` is inferred from filename substrings, not an explicit parameter — a filename change could silently misclassify.

**Validation Rules:** `format` binds to the `ExportFormat` enum (`CSV`/`XLSX`, default `CSV`); no bean-validated request DTOs.

**Edge Cases:**
- Exporting a cohort with zero rows produces a valid file with headers only.
- `exportTiers`'s row count can be less than the cohort's member count whenever any member has never been scored/tiered — easy to misread as a bug in QA without knowing this rule.

**Permissions:** `AdminExportController` — `hasRole('SUPER_ADMIN')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| EXP4-01 | Tiers export | Never-tiered members omitted | Cohort has 5 members, only 3 have tier history | Export tiers | File has exactly 3 data rows, not 5 with blanks | | |
| EXP4-02 | Scores export | Only current scores included | A student has an old, non-current score row | Export scores | That student's stale row is excluded | | |
| EXP4-03 | Empty cohort | Zero-row export still valid | Brand-new cohort, no scores yet | Export scores as XLSX | File opens cleanly with headers only | | |
| EXP4-04 | Audit | Export is logged | — | Run any export | An `EXPORT_GENERATED` row appears in the audit trail and `export_history` | | |

---

## 18. Certificate Management

**Business Purpose:** Admin-defined certificate templates (one active per type) and one-time issuance of PDF certificates to students, with branding snapshotted at issue time so later template edits never retroactively change a certificate a student already received.

**User Roles:** SUPER_ADMIN — full template CRUD, activate, preview, generate, view/download any certificate. STUDENT — view/download own. PARENT — view/download their linked student's.

**Features Included:** Template CRUD, activate (deactivates any other template of the same type), preview (renders from sample data, persists nothing); generate a certificate for a student from the active template for a type; list/download for admin, student, and parent.

**Database Tables:**
- `certificate_templates` — `name`, `certificate_type` (`TIER_1`/`TIER_2`/`TIER_3`/`COMPLETION`), `body_template` (`{{placeholder}}` text), `primary_color_hex`, `institution_name_override`, `logo_path_override`, `active` — partial unique index: one active template per type.
- `certificates` — `student_id`, `template_id`, `certificate_type` (snapshot), `certificate_number` (unique), `cohort_id`, `tier_at_issue`, `file_path`, `institution_name_snapshot`/`logo_path_snapshot`/`primary_color_snapshot`, `status` (`ISSUED` only today — a future-safe column with no revoke UI yet).

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET/POST/PUT | `/api/admin/certificate-templates` | List / create / update |
| PUT | `/api/admin/certificate-templates/{id}/activate` | Activate (deactivates same-type others) |
| GET | `/api/admin/certificate-templates/{id}/preview` | Preview PDF from sample data |
| GET/POST | `/api/admin/certificates` | List all / generate |
| GET | `/api/admin/certificates/{id}/download` | Admin download |
| GET | `/api/student/certificates` \| `/{id}/download` | List / download mine |
| GET | `/api/parent/certificates` \| `/{id}/download` | List / download linked student's |

**UI Pages:** `CertificateTemplatesPage` (`/admin/certificate-templates`), `AdminCertificatesPage` (`/admin/certificates`), `MyCertificatesPage` (`/certificates`, shared STUDENT/PARENT route, content switches by role).

**Business Rules:**
- Certificates are rendered once at generate time (via `openhtmltopdf`) and never re-rendered on download — branding is snapshotted onto the row, not read live from the template/settings.
- Generation requires the target user to actually be a STUDENT and requires an active template for the requested type — 422/404 otherwise.
- Activating a template deactivates any other active template of the same type in the same call.
- The PDF placeholder set is fixed: `studentName`, `tierName`, `cohortName`, `issueDate`, `certificateNumber` — the admin edits only the body-text template + color/branding overrides, not a visual designer.
- Certificate number format: `KBV-<year>-<typeCode>-<8-char random suffix>`.

**Validation Rules:**
- `UpsertCertificateTemplateRequest`: `name` `@NotBlank @Size(max=150)`; `certificateType` `@NotNull`; `bodyTemplate` `@NotBlank`; `primaryColorHex` `@NotBlank @Pattern` (6-digit hex).
- `GenerateCertificateRequest`: `studentId`/`certificateType` both `@NotNull`.

**Edge Cases:**
- Generating for a non-student user → `BusinessRuleException`.
- Generating when no active template exists for the type → `BusinessRuleException`.
- A student/parent requesting a certificate that isn't theirs → 404, not 403 (ownership check).
- Since branding is snapshotted, editing a template's colors/logo after certificates were already issued has **zero** effect on those existing PDFs — a common QA misunderstanding worth an explicit test.

**Permissions:** `AdminCertificateTemplateController`/`AdminCertificateController` — `hasRole('SUPER_ADMIN')`. `StudentCertificateController` — `hasRole('STUDENT')`. `ParentCertificateController` — `hasRole('PARENT')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| CERT-01 | Generate | Non-student rejected | Target user is a PARENT | POST generate with that user's id | 422 | | |
| CERT-02 | Generate | No active template blocks generation | No active `TIER_1` template exists | Generate a `TIER_1` certificate | 422 "No active certificate template for type TIER_1" | | |
| CERT-03 | Activate | Activating deactivates the sibling | Template A (TIER_1) is active; Template B (TIER_1) exists | Activate Template B | A becomes inactive, B active — exactly one active TIER_1 template | | |
| CERT-04 | Snapshot | Later template edits don't affect issued certificates | Certificate already issued from Template A | Edit Template A's color and re-download the certificate | Downloaded PDF still shows the original color (snapshot, not live template) | | |
| CERT-05 | Ownership | Student cannot download another's certificate | Certificate belongs to Student B | Student A requests download by id | 404 | | |
| CERT-06 | Preview | Preview persists nothing | — | Call preview twice | No `certificates` rows created; same sample PDF returned each time | | |

---

## 19. Generalized Export Module

**Business Purpose:** A registry-driven CSV/Excel export system for 10 additional datasets beyond the original Phase 4 four, avoiding a hand-written method per dataset. One generic endpoint + strategy-per-dataset pattern.

**User Roles:** SUPER_ADMIN only.

**Features Included:** List exportable datasets + which filters each supports; export any dataset as CSV/XLSX with optional date/cohort/student/status filters; recent export-run history (powers the "Today's Exports" dashboard card).

**Database Tables:**
- `export_history` (Phase 5, shared with the Phase 4 module) — `dataset` (CHECK-constrained to 12 values: the 2 Phase 4-only + 10 here), `format` (`CSV`/`XLSX`), `filters_snapshot`, `row_count`.
- No new content tables — each handler queries existing domain tables directly.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/export/datasets` | List datasets + supported filters |
| GET | `/api/admin/export/dataset/{dataset}` | Export (query: `format`, `from`, `to`, `cohortId`, `studentId`, `status`) |
| GET | `/api/admin/export/history` | Recent export runs (last 50) |

**Datasets** (`ExportDataset` enum, 10 values, each with its own handler class in `service/export/handlers/`): `STUDENTS`, `PARENTS`, `COHORTS`, `LESSONS`, `HOMEWORK`, `QUIZZES`, `REFLECTIONS`, `PRACTICE_LOGS`, `ANALYTICS`, `AUDIT_LOGS`. (`LEADERBOARD`/`COMPOSITE_SCORES` are deliberately absent — already served by the untouched Phase 4 endpoints.)

**UI Pages:** `DataExportPage` (`/admin/data-export`) — dataset picker + filter row.

**Business Rules:**
- Not every filter applies to every dataset — each handler declares its own `supportedFilters` set, and the frontend/`datasets` endpoint surfaces exactly which ones are relevant per dataset.
- The 4 existing Phase 4 endpoints are completely untouched — this module only adds the 10 new datasets, so nothing already shipped was put at risk.
- Every export (old and new alike) writes one `export_history` row — this is the single source for the "Today's Exports" dashboard card across both modules.

**Validation Rules:** `dataset` binds to the `ExportDataset` enum path variable (unknown value → 400 from Spring's binding); `format` defaults to `CSV`.

**Edge Cases:**
- Passing an unsupported filter for a given dataset (e.g. `status` on `STUDENTS`) is silently ignored by that handler rather than rejected — worth a dedicated "filter has no effect" QA note per dataset.
- `AUDIT_LOGS` dataset export could itself generate a large file if run with no date range — no server-side row cap visible in the handler.

**Permissions:** `AdminGenericExportController` — `hasRole('SUPER_ADMIN')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| EXP5-01 | Datasets | List reflects supported filters | — | GET `/datasets` | Each of the 10 datasets lists its actual supported filter set | | |
| EXP5-02 | Export | Old Phase 4 endpoints unaffected | — | Call `/api/admin/export/leaderboard` (Phase 4) and `/api/admin/export/dataset/STUDENTS` (Phase 5) | Both work independently; neither dataset overlaps | | |
| EXP5-03 | Export | Unsupported filter for a dataset is a no-op, not an error | `STUDENTS` dataset, pass a `status` filter | Export | 200, filter silently has no effect (verify against the dataset's declared supported filters) | | |
| EXP5-04 | History | Every export is recorded | — | Run 3 different exports | `GET /history` shows all 3, newest first | | |

---

## 20. Audit Trail

**Business Purpose:** A general-purpose, cross-cutting audit log (login, CRUD, certificates, exports, backups) via AOP, distinct from the Phase 4 score/tier-scoped `score_audit_logs`, which stays untouched as the detailed source of truth for score changes.

**User Roles:** SUPER_ADMIN only.

**Features Included:** Filterable/paginated audit trail (actor, action, entity type, date range); count of today's events (powers the "Audit Events Today" dashboard card).

**Database Tables:**
- `audit_logs` — `actor_email_snapshot` (captures an attempted email even with no resolvable user, e.g. an unknown-email failed login), `action`, `entity_type`, `entity_id`, `old_value`/`new_value` (text), `ip_address`, `user_agent`. Actor is `BaseEntity.created_by`. `action`/`entity_type` are intentionally not CHECK-constrained (the action list grows incrementally).

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/audit-trail` | List, filterable (actorId, action, entityType, date range) |
| GET | `/api/admin/audit-trail/today-count` | Today's event count |

**Instrumented actions** (via `@Audited` on the method): `LOGIN`, `LOGIN_FAILED`, `LOGOUT`, `USER_CREATED`, `USER_UPDATED`, `USER_STATUS_CHANGED`, `LESSON_CREATED`, `QUIZ_EDITED`, `HOMEWORK_DELETED`, `REFLECTION_EDITED`, `PRACTICE_APPROVED`, `TIER_OVERRIDDEN`, `CERTIFICATE_GENERATED`, `EXPORT_GENERATED`, `BACKUP_CREATED` — an explicit, curated list, not every write path in the app.

**UI Pages:** `AuditTrailPage` (`/admin/audit-trail`).

**Business Rules:**
- Path collision deliberately avoided: this lives at `/api/admin/audit-trail`, distinct from the existing score-scoped `/api/admin/audit-logs` (see [Scoring Engine](#13-scoring-engine)) — nothing Phase 4 built moved or broke.
- `@Audited(captureResult=false)` is used on `LOGIN` specifically — a security fix that stops the raw JWT access token from being captured as the `new_value` (an earlier version of this aspect blindly `toString()`'d the login response).
- IP/User-Agent capture (`RequestContextHolder`) only works on the original request thread — never call an audited method from an async job or `@Scheduled` task expecting these to populate.
- On failure, the `@Around` advice still records a `failureAction` entry (e.g. `LOGIN_FAILED`) before rethrowing — auditing survives the caller's own transaction rollback via its own `REQUIRES_NEW` write.

**Validation Rules:** No request-body DTOs (all query params).

**Edge Cases:**
- A failed login for an unrecognized email still gets an audit row (via `actor_email_snapshot`) even though there's no `User` to attribute it to.
- Calling an `@Audited` method from a background/scheduled context silently loses IP/User-Agent (both null) — not an error, just missing detail.

**Permissions:** `AdminAuditTrailController` — `hasRole('SUPER_ADMIN')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| AUDIT-01 | Login | Failed login for unknown email is still logged | — | Attempt login with a nonexistent email | A `LOGIN_FAILED` row appears with `actor_email_snapshot` set and `actor_id` null | | |
| AUDIT-02 | Security | Login success never logs the raw token | — | Log in successfully, inspect the audit row | `new_value` is null/absent, not the JWT | | |
| AUDIT-03 | Path | Score audit and general audit trail are independent | — | Change score config (writes to `score_audit_logs`) and override a tier (writes to `audit_logs`) | `/api/admin/audit-logs` shows only the score event; `/api/admin/audit-trail` shows only the tier override | | |
| AUDIT-04 | Rollback survival | Audit entry survives the caller's rollback | Trigger a login failure (throws an exception after auditing) | Check `audit_logs` | The `LOGIN_FAILED` row persists despite the enclosing request failing | | |

---

## 21. System Settings

**Business Purpose:** Single admin-editable settings row (branding, locale, upload limits, security policy, feature toggles) mirroring the `score_config` single-active-row pattern, plus a public-safe subset for unauthenticated bootstrap (login page branding, maintenance-mode check).

**User Roles:** SUPER_ADMIN — get/update. All roles (including unauthenticated) — the public-safe subset.

**Features Included:** Get/update the full settings row; public-safe branding + maintenance-mode bootstrap endpoint; maintenance mode enforcement (503 for non-admins); dynamic password policy; per-request allowed-file-types/max-size consulted by Lessons/Homework; bounded CSS-custom-property theming (primary/secondary/accent color, logo, app/institution name) applied via a frontend `ThemeProvider`.

**Database Tables:**
- `system_settings` — single active row (partial unique index): `application_name`, `institution_name`, `logo_path`, 3 color hexes, `timezone`, `date_format`, `max_file_size_mb`, `allowed_file_types` (CSV), `max_login_attempts` (1–20), `password_min_length` (6–128) + 4 character-class booleans, `session_timeout_minutes` (5–43200, maps to refresh-token TTL, not the 15-min access token), `maintenance_mode`, `certificate_enabled`, `export_enabled`, `active`. Seeded with one default row on migration.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET/PUT | `/api/admin/settings` | Get / update (SUPER_ADMIN) |
| GET | `/api/settings/public` | Public-safe subset (unauthenticated) |

**UI Pages:** `SettingsPage` (`/admin/settings`) — sectioned form (Branding, Colors, Locale, Uploads, Security, Feature toggles).

**Business Rules:**
- Single-row upsert: `update()` always mutates the one active row in place, never creates a second row (see [Testing Infrastructure](#28-testing-infrastructure) for the dedicated regression test).
- `certificate_enabled`/`export_enabled` gate those Phase 5 features the same way `leaderboard_enabled` (Phase 4, still on `score_config`) already gates the leaderboard — not duplicated onto this table.
- Maintenance mode: when on, every request from a non-SUPER_ADMIN caller gets 503, except `/api/auth/*`, `/api/settings/public`, docs, and `/actuator/*` — an admin can still sign in and flip it back off.
- `LessonFileServiceImpl`/`HomeworkSubmissionServiceImpl` consult `allowed_file_types`/`max_file_size_mb` from here as their default (homework can still override per-assignment).
- The active-row lookup is Caffeine-cached (Phase 5 Step 7) with eviction on every `update()` — see [Performance & Caching](#25-performance--caching).

**Validation Rules:** `UpdateSystemSettingsRequest`: color hexes `@Pattern` (6-digit); `maxLoginAttempts`/`passwordMinLength`/`sessionTimeoutMinutes`/`maxFileSizeMb` all `@Min` bounded to match the DB CHECK constraints; `applicationName`/`institutionName`/`timezone`/`dateFormat`/`allowedFileTypes` `@NotBlank`.

**Edge Cases:**
- No active row (should never happen given the seed + partial unique index, but the service throws `ResourceNotFoundException` defensively if it ever does).
- Reducing `session_timeout_minutes` only affects **newly issued** refresh tokens — there's no revocation/blocklist to retroactively shorten a token a user already holds.
- Maintenance mode's exemption list is a hardcoded prefix check (`isExempt`) — a new public endpoint added later must be added here too or it will 503 during maintenance.

**Permissions:** `AdminSystemSettingsController` — `hasRole('SUPER_ADMIN')`. `PublicSettingsController` — none (public).

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| SET-01 | Upsert | Update mutates the same row | — | Update settings twice in a row, inspect the row `id` both times | Same `id` both times — no duplicate rows created | | |
| SET-02 | Maintenance | Non-admin blocked, admin unaffected | `maintenance_mode=true` | STUDENT hits any protected endpoint; SUPER_ADMIN hits the same | Student gets 503 `MAINTENANCE_MODE`; admin succeeds normally | | |
| SET-03 | Maintenance | Public/auth endpoints stay reachable | `maintenance_mode=true` | Unauthenticated call to `/api/settings/public` and `/api/auth/login` | Both succeed despite maintenance mode | | |
| SET-04 | Password policy | Dynamic policy enforced on admin-created users | `password_require_digit=true` | Admin creates a user with a password containing no digit | 422 (see [User Management](#2-user-management) for the student/parent-creation gap) | | |
| SET-05 | Cache | Update evicts the cache immediately | — | Read a setting, update it, read again immediately | Second read reflects the new value, not a stale cached one | | |

---

## 22. Backup & Restore

**Business Purpose:** Manual, admin-triggered full database backups via `pg_dump`, downloadable and deletable — no scheduling. Restore is an explicit disabled placeholder, not a real feature yet.

**User Roles:** SUPER_ADMIN only.

**Features Included:** Create a backup (synchronous `pg_dump`); list backup history; download a backup file; delete a backup (removes both file and history record); a visible-but-disabled "Restore" control with explanatory text.

**Database Tables:**
- `backup_history` — `file_path`, `file_size_bytes`, `status` (`IN_PROGRESS`/`COMPLETED`/`FAILED`), `error_message`.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/admin/backups` | Create (runs `pg_dump` synchronously) |
| GET | `/api/admin/backups` | List history |
| GET | `/api/admin/backups/{id}/download` | Download |
| DELETE | `/api/admin/backups/{id}` | Delete (file + record) |

**UI Pages:** `BackupPage` (`/admin/backups`) — Create button, history table, Download/Delete row actions, disabled Restore control.

**Business Rules:**
- Runs `pg_dump` via `ProcessBuilder`, parsing host/port/dbname from the configured JDBC URL; password passed via the subprocess environment (`PGPASSWORD`), **never** the command line (avoids leaking it in `ps` output).
- **Deliberately not `@Transactional`**: `pg_dump` can run for minutes, and holding a connection-pool slot for that whole span risks exhausting the pool. Each `backupHistoryRepository.save()` call commits as its own short transaction instead.
- A 5-minute timeout (`TIMEOUT_MINUTES`); stderr is drained on a background thread *while* `waitFor(timeout)` runs — reading it synchronously first would defeat the timeout entirely (a real bug found and fixed during Phase 5 Step 6).
- A non-zero exit code records `status=FAILED` with the captured stderr, never a silently-corrupt "success."
- Restore has no backend implementation — the UI control exists but is disabled by design (decision made explicit in the plan, not an oversight).
- The backend Docker image installs `postgresql-client` specifically so `pg_dump` is available in the container — see [Deployment & Infrastructure](#27-deployment--infrastructure).

**Validation Rules:** None (no request body on any endpoint).

**Edge Cases:**
- A hung `pg_dump` process is killed at the 5-minute timeout and recorded as `FAILED`.
- Deleting a backup whose file was already manually removed from disk still cleans up the DB record (best-effort file delete).
- Downloading a backup mid-creation (`IN_PROGRESS`) — the file may not exist yet or be incomplete; no explicit guard against this in the download path.

**Permissions:** `AdminBackupController` — `hasRole('SUPER_ADMIN')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| BKP-01 | Create | Successful backup | Postgres reachable, `pg_dump` on PATH | POST create | `status=COMPLETED`, file exists at the recorded path, correct file size | | |
| BKP-02 | Create | Failure is recorded, not silent | Simulate a `pg_dump` failure (e.g. bad credentials) | POST create | `status=FAILED`, `error_message` populated with the captured stderr | | |
| BKP-03 | Download | Downloads the exact stored file | A completed backup exists | GET download | File bytes match what's on disk | | |
| BKP-04 | Delete | Removes both file and record | A completed backup exists | DELETE it | File removed from disk; `GET list` no longer shows it | | |
| BKP-05 | Restore | Restore control is disabled | — | View the Backups page | Restore button is visibly present but disabled, with explanatory text | | |

---

## 23. Security Hardening

**Business Purpose:** A set of cross-cutting protections added in Phase 5 Step 7: account lockout, password policy, rate limiting, security headers, and input sanitization. No single UI page — surfaced through Users (unlock), Settings (policy fields, maintenance mode), and response headers.

**User Roles:** SUPER_ADMIN — unlock accounts, configure policy. All roles — protected equally by rate limiting, lockout, and headers.

**Features Included:**
- Account lockout after N failed logins (see [Authentication](#1-authentication)) + admin unlock.
- Dynamic password-policy validation (min length + character-class requirements) at user creation/reset.
- Sliding-window rate limiting (10 requests/60 seconds per IP+path) on `/api/auth/login`, `/refresh`, `/forgot-password`.
- Security response headers: HSTS (HTTPS only), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, a baseline Content-Security-Policy (`default-src 'self'; frame-ancestors 'none'; object-src 'none'`).
- Input sanitization (trim, strip control characters, enforce max length) on free-text fields: audit/override reason, notification message.
- CSRF explicitly not enabled — documented as correct given stateless bearer-token auth with no ambient cookie credential to ride on.

**Database Tables:** `users.failed_login_attempts`/`locked_until` (see Authentication); no new tables of its own.

**APIs:** `PUT /api/admin/users/{id}/unlock` (see [User Management](#2-user-management)). No other dedicated endpoints — this module is filters/validators/config, not a CRUD surface.

**UI Pages:** "Locked" badge + Unlock action on `UsersPage`; password-policy fields + maintenance-mode toggle on `SettingsPage`.

**Business Rules:**
- `RateLimitFilter` is in-memory, single-JVM only (a multi-instance deployment would need a shared store like Redis) — a documented, proportionate limitation.
- `LoginAttemptService.recordFailedAttempt` runs in its own `REQUIRES_NEW` transaction specifically because the original inline implementation was silently losing the increment: `AuthServiceImpl.login()` is `@Transactional`, and throwing `ApiException(INVALID_CREDENTIALS)` right after recording the attempt rolled back the *entire* transaction, discarding the counter write — accounts never actually locked no matter how many wrong passwords were tried. This was found and fixed via live verification during Phase 5 Step 7 (see the `AuthControllerIntegrationTest` in [Testing Infrastructure](#28-testing-infrastructure), which specifically exercises this end-to-end).
- HSTS is only emitted over an actual HTTPS connection — its absence on plain HTTP in local dev is correct Spring Security behavior, not a bug.
- Password policy is dynamic (from `system_settings`) but only wired into direct admin user creation/reset — see the gap noted in [User Management](#2-user-management).

**Validation Rules:** See `PasswordPolicyValidator` — violations are aggregated into one message listing every unmet requirement (e.g. "Password must contain at least 8 characters, an uppercase letter, a digit").

**Edge Cases:**
- Rate limiting counts **all** requests to a limited path regardless of outcome (success or failure) — a burst of valid logins can trip it just as easily as a brute-force attempt.
- The rate limiter and the lockout counter interact: a determined attacker hits the rate limit (429) well before exhausting more than a handful of lockout attempts, since both share the same request stream.
- Sanitization max-lengths don't always match their DB column lengths exactly (see the [Notifications](#12-notifications) message-length mismatch) — a class of bug worth a systematic audit beyond what Phase 5 covered.

**Permissions:** Rate limiting and headers apply to every request indiscriminately (filter-level, not role-based). Unlock is `hasRole('SUPER_ADMIN')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| SEC-01 | Lockout | End-to-end lockout via real transaction boundary | Fresh test account | 5 wrong-password attempts, then a 6th | 6th returns 423, not 401 — verifies the counter actually persists across the rollback-prone code path | | |
| SEC-02 | Rate limit | 11th request in the window is throttled | — | 11 rapid requests to `/api/auth/login` | 11th is 429 `RATE_LIMITED` | | |
| SEC-03 | Headers | Security headers present on every response | — | Inspect headers on any API response | `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, CSP present | | |
| SEC-04 | Password policy | All violations listed at once | Policy requires upper/lower/digit/special, min 10 | Submit a password missing 3 of the 4 requirements | Single error message lists all 3 missing requirements together | | |
| SEC-05 | Sanitization | Control characters stripped from free text | — | Submit a tier-override reason containing control characters | Stored value has them stripped, not rejected | | |

---

## 24. Error Monitoring

**Business Purpose:** Persists a tiered-severity feed of unhandled exceptions and auth/authz failures so admins have a real error dashboard, without flooding it with routine client-side validation misses.

**User Roles:** SUPER_ADMIN only.

**Features Included:** Automatic capture (via `GlobalExceptionHandler`) of unhandled exceptions (ERROR), authentication/authorization failures and oversized-upload rejections (WARNING); paginated, severity-filterable admin log viewer.

**Database Tables:**
- `application_logs` — `severity` (CHECK `ERROR`/`WARNING`), `source` (exception class name), `message`, `stack_trace_excerpt` (truncated to 4000 chars, ERROR only), `endpoint`, `http_method`, `ip_address`.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/application-logs` | List, filterable by severity, paginated |

**UI Pages:** `ApplicationLogsPage` (`/admin/application-logs`).

**Business Rules:**
- Severity is deliberately tiered, not indiscriminate: unhandled `Exception` → ERROR (with stack trace); `AuthenticationException`, `AccessDeniedException`, `MaxUploadSizeExceededException` → WARNING (no stack trace); routine `MethodArgumentNotValidException`/`ConstraintViolationException` (400s) and ordinary `ApiException`s (business-rule violations) are **not** persisted here at all — logging every form-validation miss at the same severity as a real incident would flood the table and defeat the point.
- The write is `REQUIRES_NEW` + try/catch (never throws) — logging an error must never itself break the error path.
- `AuthenticationException`/`AccessDeniedException` thrown by Spring Security's own filter-chain-level checks (e.g. no/invalid JWT, unauthenticated request to a protected URL) are handled by dedicated `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` classes and **never reach** `GlobalExceptionHandler` — only `AccessDeniedException` from a method-level `@PreAuthorize` failure (right token, wrong role) inside the MVC dispatch actually reaches the handler and gets logged here. This is a real architectural nuance worth understanding before assuming "every 401/403 shows up in this log."

**Validation Rules:** None (read-only viewer; writes are internal-only, triggered by `GlobalExceptionHandler`).

**Edge Cases:**
- A raw "missing token" 401 does **not** appear in this log (filter-level, bypasses `GlobalExceptionHandler` entirely) — only a "valid token, wrong role" 403 from `@PreAuthorize` does. Verified live during Phase 5 Step 7: repeated no-token/garbage-token requests produced zero entries, while a real `@PreAuthorize` denial produced exactly one.
- Stack traces are truncated at 4000 characters — a very deep exception chain loses its tail.

**Permissions:** `AdminApplicationLogController` — `hasRole('SUPER_ADMIN')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| ERR-01 | Tiering | Right-token-wrong-role 403 is logged | Non-admin authenticated user | Hit an admin-only endpoint with a valid non-admin token | A WARNING entry appears with `source=AccessDeniedException` | | |
| ERR-02 | Tiering | No-token 401 is NOT logged | — | Hit any protected endpoint with no `Authorization` header | 401 returned, but **no** entry appears in `application_logs` | | |
| ERR-03 | Tiering | Routine validation failure is NOT logged | — | Submit a form with a missing required field | 400 returned, no entry appears in `application_logs` | | |
| ERR-04 | Filter | Severity filter works | Mix of ERROR and WARNING entries exist | GET with `severity=ERROR` | Only ERROR entries returned | | |
| ERR-05 | Upload | Oversized upload is logged as WARNING | `max_file_size_mb` exceeded | Upload an oversized file | 400, and a WARNING entry with `source=MaxUploadSizeExceededException` appears | | |

---

## 25. Performance & Caching

**Business Purpose:** Caffeine-backed in-memory caching on the hottest single-active-row lookups (`ScoreConfig`, `SystemSettings` — read on nearly every request/scoring call), plus a real `/actuator/health` endpoint backing a whitelist that existed since Phase 1 but previously 404'd.

**User Roles:** Transparent to all roles — a performance layer, not a user-facing feature.

**Features Included:** `@Cacheable` on `ScoreConfigRepository.findByActiveTrueAndDeletedFalse()` and `SystemSettingsRepository.findByActiveTrueAndDeletedFalse()`, `@CacheEvict` on both services' `update()`; Spring Boot Actuator with only the `health` endpoint exposed.

**Database Tables:** None — pure application-layer caching, no schema impact.

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/actuator/health` | Liveness/readiness check (public) |

**UI Pages:** None directly — the "System Health" admin dashboard card reflects a related but separate disk-space self-check (see [Dashboards](#4-dashboards)), not this endpoint.

**Business Rules:**
- Caching is applied at the **repository** layer (not just the service layer), so it transparently covers every call site — including the handful of services (`ExportServiceImpl`, `ScoreEngineServiceImpl`, `LeaderboardServiceImpl`) that call the repository directly rather than through `ScoreConfigServiceImpl`.
- Cache eviction (`allEntries=true`) fires on every `update()` call — a stale read immediately after an update is not expected; verified via a direct before/update/after read sequence during Step 7.
- Caffeine spec: `maximumSize=10, expireAfterWrite=10m` for both caches — generous given each holds at most one entry (a single active row), so size is not the limiting factor, just a safety cap.
- `/actuator/health` exposure is deliberately minimal — only `health`, not the full actuator surface (no `/actuator/env`, `/actuator/beans`, etc.), to avoid leaking internal configuration.

**Validation Rules:** None.

**Edge Cases:**
- If a row is ever updated by a path that bypasses both `ScoreConfigServiceImpl.update()`/`SystemSettingsServiceImpl.update()` (e.g. a raw SQL update in an emergency), the cache would serve stale data until the 10-minute TTL expires — no external cache-bust mechanism exists.
- The health check reflects only "is the app up and can it serve a response" — it does not verify database connectivity by default beyond Spring Boot Actuator's own default health indicators (DB, disk space) which are auto-configured but not customized here.

**Permissions:** `/actuator/health` is public (whitelisted in `AppConstants.PUBLIC_ENDPOINTS`, matches the container `HEALTHCHECK`).

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| CACHE-01 | Eviction | Update immediately reflects in a subsequent read | — | Read a setting, update it, read again immediately | Second read shows the new value, not the cached old one | | |
| CACHE-02 | Coverage | Direct repository callers also see cached data | — | Trigger a scoring recalculation (uses `ScoreConfigRepository` directly, not via the service) | No unnecessary extra DB round-trip for the config lookup (verify via query logging) | | |
| CACHE-03 | Health | Endpoint is public and returns UP | — | Unauthenticated GET `/actuator/health` | 200, `{"status":"UP"}` | | |
| CACHE-04 | Health | Only `health` is exposed | — | GET `/actuator/env` or `/actuator/beans` | 404 — not exposed | | |

---

## 26. Global Search

**Business Purpose:** A single search box for admins to quickly find students, cohorts, lessons, and other records by partial text match, without navigating to each module's own list page.

**User Roles:** SUPER_ADMIN only — the searchable set spans other students' PII (email), so it's scoped to the one role that can already see all of it via the admin module.

**Features Included:** Debounced live-dropdown search in the Topbar (top 5 matches per type); a full results page grouped by entity type.

**Database Tables:** No new tables — plain `ILIKE`-equivalent (`lower(x) like lower('%q%')`) JPQL queries across 9 existing entity types' repositories, no `pg_trgm`/GIN index (deliberately avoided — that extension needs superuser privileges not guaranteed in every hosting environment; documented as a future optimization).

**Searchable entity types (9):** `USER` (name/email), `COHORT` (name), `LESSON` (title), `HOMEWORK` (title), `QUIZ` (title), `REFLECTION_QUESTION` (question text), `PRACTICE_SESSION` (subject), `CERTIFICATE` (certificate number), `AUDIT_LOG` (action/entity type/actor email).

**APIs:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/search?q=` | Search across all 9 types, capped at 5 results per type |

**UI Pages:** `GlobalSearchBar` (in the Topbar, admin-only), `SearchResultsPage` (`/search`).

**Business Rules:**
- Query must be at least 2 characters — shorter queries return an empty list without hitting the database.
- Results are capped at 5 per type in the dropdown/API response — no pagination within a single search call, by design (this is a quick-jump tool, not a full search engine).
- Each entity type's search covers only a small, deliberately chosen set of columns (e.g. `USER` searches email + first + last name, not phone).

**Validation Rules:** `q` is a required query param; no server-side max-length enforced.

**Edge Cases:**
- A query matching many records across all 9 types still returns at most 45 results total (5×9) — a very common term (e.g. "a") could still feel incomplete/surprising without pagination.
- Since this is role-gated to SUPER_ADMIN only, the Topbar search bar simply doesn't render for STUDENT/PARENT — there is no degraded/partial student-facing search.

**Permissions:** `SearchController` — `hasRole('SUPER_ADMIN')`.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| SRCH-01 | Search | Cross-type match | A cohort and a user both contain "test" in their name | Search "test" | Results include both, grouped correctly by type | | |
| SRCH-02 | Search | Minimum query length | — | Search with 1 character | Empty result, no DB query executed | | |
| SRCH-03 | Search | Per-type cap | 8 users match "student" | Search "student" | Exactly 5 USER results returned, not 8 | | |
| SRCH-04 | Access | Non-admin cannot search | Logged in as STUDENT | GET `/api/search?q=test` | 403; search bar is also not rendered in the Topbar | | |
| SRCH-05 | Case | Search is case-insensitive | Cohort named "Test Cohort" | Search "TEST" | Match found | | |

---

## 27. Deployment & Infrastructure

**Business Purpose:** Docker-based deployment for the full stack (Postgres + backend + frontend), production-grade logging, and CI, so the platform can run somewhere other than a developer's laptop.

**User Roles:** N/A — operational, not user-facing.

**Features Included:**
- Backend `Dockerfile`: multi-stage Maven→JRE 21 build, installs `postgresql-client` (for the Backup module's `pg_dump`), runs as a non-root user, `HEALTHCHECK` against `/actuator/health`.
- Frontend `Dockerfile`: multi-stage Node build → static files served by nginx, which reverse-proxies `/api/*` to the backend container so the browser only ever talks to one origin.
- `docker-compose.yml` orchestrating Postgres + backend + frontend with named volumes for DB data, uploaded-file storage, and rotated logs.
- `logback-spring.xml`: console-only in `dev` (unchanged from every prior phase); console + size/time-rotated file appender in every other profile.
- `application-prod.yml`: dials the dev-only DEBUG app logging back to INFO.
- `.github/workflows/ci.yml` (both repos): backend runs `mvn test` against a `postgres:16-alpine` service container; frontend runs typecheck, lint, test, and build — on every push/PR.
- `DEPLOYMENT.md`: full setup guide (env vars, `docker compose up`, data persistence, backup-in-container notes, log locations).

**Database Tables:** None.

**APIs:** None of its own — relies on `/actuator/health` (see [Performance & Caching](#25-performance--caching)) as the container health check.

**UI Pages:** None.

**Business Rules:**
- `docker-compose.yml` assumes the two repos are checked out as **sibling directories** (`kbv-education/` containing the compose file and `kbveducation-api/`, next to a sibling `kbveducation-web/`) — an explicit, documented layout requirement, not auto-detected.
- The frontend's `VITE_API_BASE_URL` stays `/api` (the default) in the containerized build specifically so it works unmodified behind nginx's reverse proxy — overriding it would bypass the proxy and require the browser to resolve the backend's container-internal hostname, which it can't.
- Compose refuses to start the backend without a real `JWT_SECRET` and `ADMIN_PASSWORD` (Compose's `${VAR:?message}` required-variable syntax) — no silent fallback to the insecure dev placeholder in a "production" compose run.
- Docker itself was not available in the development environment this was built in — the images were reviewed by hand and cross-validated against equivalent local smoke tests (a full `mvn compile`/`test`, a `SPRING_PROFILES_ACTIVE=prod` local boot confirming the rolling log file writes correctly, and a `npm run build`) rather than an actual `docker build`. **This is the one part of Phase 5 that has not been verified by actually building and running the containers** — flag as the top priority for a real CI/staging environment to validate before this is trusted for a production cutover.

**Validation Rules:** N/A.

**Edge Cases:**
- A non-root container user needs the storage and log-directory mount points pre-created with correct ownership in the image *before* a fresh named volume is first mounted over them, or writes fail — handled explicitly in the Dockerfile, but worth a first-boot smoke test in any new environment.
- Postgres, backend, and frontend all start in parallel by default in Compose; the backend `depends_on: db (service_healthy)` waits for Postgres's own health check, but Flyway migrations still run at backend startup regardless of how long that takes.

**Permissions:** N/A.

**Test Cases:**
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| DEP-01 | Compose | Full stack boots | Docker + Compose available, sibling repo layout, `.env` configured | `docker compose up --build` | All 3 containers report healthy; app reachable at the frontend port | **Not yet run — Docker unavailable in the build environment** | Highest-priority verification before any real deployment |
| DEP-02 | Backend image | Health check passes | Backend container running | `docker inspect` the container's health status | `healthy`, `/actuator/health` returns 200 | Not yet run | |
| DEP-03 | Backup in container | `pg_dump` works inside the backend container | Backend container running, DB reachable | Trigger a backup via the API | Backup completes successfully (validates `postgresql-client` is actually installed and on PATH) | Not yet run | |
| DEP-04 | Frontend proxy | `/api/*` reaches the backend | Full stack running | Load the frontend, log in | Login succeeds via the nginx-proxied `/api` path, no CORS/network errors in the browser console | Not yet run | |
| DEP-05 | CI | Backend CI passes on a clean checkout | — | Push to the repo / open a PR | GitHub Actions `mvn test` job passes against the Postgres service container | | Verified structurally (workflow reviewed); actual GitHub Actions run not observed in this environment |
| DEP-06 | CI | Frontend CI passes on a clean checkout | — | Push to the repo / open a PR | GitHub Actions typecheck/lint/test/build job passes | | Same caveat as DEP-05 |
| DEP-07 | Logging | Prod profile writes rotated log files | `SPRING_PROFILES_ACTIVE=prod` | Boot the app, generate some log activity | A log file appears at the configured `LOG_PATH`, console output also present | Verified locally (non-Docker) | |

---

## 28. Testing Infrastructure

**Business Purpose:** Neither repo had any test tooling before Phase 5 Step 10. This module covers what was added: JaCoCo coverage reporting and 5 risk-prioritized backend tests; Vitest + Testing Library and 4 risk-prioritized frontend tests; CI wiring for both.

**User Roles:** N/A — developer/CI-facing.

**Features Included:**
- Backend: JaCoCo Maven plugin (`mvn test` produces a coverage report at `target/site/jacoco`); 5 test classes chosen for risk/value, not blanket coverage.
- Frontend: Vitest + jsdom + `@testing-library/react`/`user-event`/`jest-dom`; 4 test files, same risk-prioritized approach.
- CI: both repos' `ci.yml` run the respective test suites on every push/PR.

**Backend test classes** (`kbveducation-api/src/test/java/...`):
- `LoginAttemptServiceImplTest` (unit) — the account-lockout counter logic in isolation.
- `SystemSettingsServiceImplTest` (unit) — the single-active-row upsert pattern.
- `CertificateServiceImplTest` (unit, PDF renderer + storage mocked) — generation business rules (student-must-exist, must-be-STUDENT, active-template-must-exist).
- `UserRepositorySearchTest` (`@DataJpaTest`, real Postgres, transaction rolled back after each test) — the Global Search `ILIKE` query.
- `AuthControllerIntegrationTest` (`@SpringBootTest` + `MockMvc`, real filter chain) — login → 5× failed → 423 `ACCOUNT_LOCKED` → 429 `RATE_LIMITED`, end to end.

**Frontend test files** (`kbveducation-web/src/...`):
- `Button.test.tsx` — render, click, `isLoading` disables + suppresses the click.
- `Modal.test.tsx` — the accessibility fix (focus moves in on open, Tab wraps at the boundary, Escape/backdrop both close, focus returns to the trigger on close).
- `ExportButtons.test.tsx` — CSV/Excel buttons call `downloadFile` with the right url/filename (`download.ts` mocked).
- `ApplicationLogsPage.test.tsx` — one page-level test with the API module mocked (renders entries, empty state, severity filter re-queries).

**Business Rules:**
- **`AuthControllerIntegrationTest` is the test that actually caught a real production bug** during this same step: the account-lockout counter was silently rolling back due to a `@Transactional` + thrown-exception interaction (see [Security Hardening](#23-security-hardening)). The equivalent unit test (`LoginAttemptServiceImplTest`) mocks the repository and **cannot** observe a transaction rollback — this is a concrete, in-code example of why an integration test through the real transaction manager was worth writing even though a unit test already existed for the same logic.
- `@DataJpaTest` runs against the same real Postgres instance the app's `DB_URL` points at — no H2/Testcontainers dependency was introduced, matching the project's existing "always test against real Postgres" discipline. This means `mvn test` requires a reachable, migrated Postgres database — it will not run standalone/offline.
- Coverage is reported honestly, not against an arbitrary target, since both codebases had zero tests before this phase.

**Validation Rules:** N/A.

**Edge Cases:**
- The Modal focus-trap tests initially assumed the wrong initial-focus target (the body's "First" button) before discovering the header's own Close (×) button is actually first in DOM order and receives focus first — a reminder that focus-order assumptions should always be verified against the actual DOM, not the visual layout.
- `RateLimitFilter`'s in-memory state is scoped to the whole Spring test context — `AuthControllerIntegrationTest` deliberately keeps its entire login→lockout→rate-limit scenario in one test method (not split across several) specifically to avoid cross-test request-count pollution.

**Permissions:** N/A.

**Test Cases** (meta — these describe how to verify the test suite itself, not application features):
| Test ID | Feature | Scenario | Preconditions | Steps | Expected Result | Pass/Fail | Remarks |
|---|---|---|---|---|---|---|---|
| TEST-01 | Backend suite | All tests pass on a clean run | Local Postgres reachable with migrations applied | `mvn test` | 13/13 tests pass; JaCoCo report generated at `target/site/jacoco` | | Confirmed: 14.8% instruction / 14.4% line / 4.9% branch / 15.8% method / 40.2% class coverage as of this phase |
| TEST-02 | Frontend suite | All tests pass on a clean run | — | `npm test` | 17/17 tests pass | | |
| TEST-03 | Frontend coverage | Coverage report generates | — | `npm run test:coverage` | v8 coverage report produced; ~3% statement coverage (honest, first-pass number) | | |
| TEST-04 | Regression | The lockout bug stays fixed | — | Re-run `AuthControllerIntegrationTest` after any future change to `AuthServiceImpl`/`LoginAttemptService` | Test continues to pass — guards against the exact transaction-rollback regression found in Step 7 | | Treat a failure here as high-priority — it's the one test proven to catch a real bug |
| TEST-05 | CI | Both CI workflows are green | — | Push a commit | Backend and frontend `ci.yml` both pass | | See [Deployment & Infrastructure](#27-deployment--infrastructure) DEP-05/DEP-06 |

---

*Document generated during Phase 5 Step 11, grounded in the implemented API surface, database schema, and frontend routes as of this session. Phase 1–4 module details were independently verified by two research passes reading the actual Flyway migrations, entities, controllers, and service implementations rather than relying on memory. Phase 5 module details reflect this session's own implementation and live verification work.*
