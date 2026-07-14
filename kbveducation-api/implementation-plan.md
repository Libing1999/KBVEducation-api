# KBV Education – Phase 4 Development

Phase 3 has been successfully completed.

Do NOT modify or break any existing functionality.

Build Phase 4 on top of the existing application while maintaining the same architecture, coding standards, and UI consistency.

---

# Objective

Implement the complete scoring engine, graduation tier logic, leaderboard, configurable scoring system, and analytics dashboard.

This phase contains the core business logic of the KBV Education platform.

---

# Technology Stack

Continue using the existing technology stack.

Frontend

* React 19
* TypeScript
* TailwindCSS
* TanStack Query
* Zustand

Backend

* Spring Boot 3
* Java 21
* PostgreSQL
* Spring Security
* Flyway
* JWT

---

# New Database Tables

Create Flyway migrations and entities for:

score_config

student_scores

tier_rules

tier_history

leaderboard_snapshot

dashboard_metrics

score_audit_logs

Use UUID primary keys.

Include audit fields.

Support soft delete where applicable.

---

# Configurable Score Engine

The scoring system must NOT use hardcoded values.

Create an Admin Configuration page where all scoring weights can be changed.

Default values:

Practice = 60%

Reflection = 20%

Homework = 10%

Quiz = 10%

Total must always equal 100%.

Validation:

* Prevent saving if total weight ≠ 100%.
* Display validation message.

Future administrators must be able to modify these values without redeployment.

---

# Composite Score Calculation

Automatically calculate each student's Composite Score.

Formula:

Composite Score =
(Practice % × Practice Weight)
+
(Reflection % × Reflection Weight)
+
(Homework % × Homework Weight)
+
(Quiz % × Quiz Weight)

Recalculate whenever:

* Reflection changes
* Practice changes
* Homework changes
* Quiz changes
* Score configuration changes

Use backend services for all calculations.

Never calculate scores on the frontend.

---

# Practice Percentage

Calculate:

Practice %

=

Study Days

/

Available Practice Days

×

100

Exclude admin-voided days.

Round to two decimal places.

---

# Reflection Percentage

Calculate:

Reflection %

=

Reflection Days

/

Available Reflection Days

×

100

Exclude admin-voided days.

---

# Homework Percentage

Calculate:

Submitted Homework

/

Total Homework

×

100

---

# Quiz Percentage

Calculate:

Average score of completed quizzes.

Ignore incomplete quizzes.

---

# Admin Configuration

Create a complete Settings page.

Allow editing:

Practice Weight

Reflection Weight

Homework Weight

Quiz Weight

Practice Window Start

Reflection Window Start

Reflection Window End

Total Reflection Days

Total Homework Count

Enable/Disable Leaderboard

Enable/Disable Dashboard Widgets

Changes should take effect immediately.

---

# Graduation Tier Rules

Implement configurable tier rules.

Default rules:

Tier 1

Composite ≥ 90

Practice ≥ 88

Full Papers ≥ 12

Tier 2

Composite 80–89

Practice ≥ 83

Full Papers ≥ 6

Tier 3

Composite 60–79

Practice ≥ 71

Not Passing

Composite < 60

These values must be editable by the admin.

---

# Tier Engine

Automatically determine:

Current Tier

Next Possible Tier

Remaining Requirements

Never permanently assign a tier.

Store:

Calculated Tier

Confirmed Tier

Override Reason

Admin can:

Confirm calculated tier

Override tier

Enter override reason

View tier history

---

# Leaderboard

Display leaderboard within each cohort only.

Student View:

Rank

Name

Composite Score

Current Tier

Practice %

Reflection %

Homework %

Quiz %

Students must never see other students' reflections, homework, or practice logs.

Only rankings and scores.

---

# Leaderboard Configuration

Admin can:

Enable/Disable leaderboard

Choose sorting:

Composite

Practice

Quiz

Reflection

Homework

Default:

Composite Score Descending.

---

# Analytics Dashboard

Admin dashboard should display:

Average Composite Score

Highest Score

Lowest Score

Average Practice %

Average Reflection %

Average Homework %

Average Quiz %

Tier Distribution

Active Students

Students at Risk

Weekly Activity

Monthly Activity

---

# Charts

Create charts using a React chart library.

Display:

Composite Score Distribution

Practice Trend

Reflection Trend

Homework Completion

Quiz Performance

Tier Distribution

Leaderboard Trend

Charts must be responsive.

---

# Student Dashboard Improvements

Show:

Composite Score

Current Tier

Score Breakdown

Progress Toward Next Tier

Reflection Progress

Practice Progress

Homework Progress

Quiz Progress

Recent Activities

Leaderboard Position

---

# Parent Dashboard

Parents can see:

Composite Score

Current Tier

Practice %

Reflection %

Homework %

Quiz %

Progress Charts

No editing.

---

# Audit Logs

Record every score-related change.

Log:

Previous Value

New Value

User

Date

Reason

Examples:

Weight changed

Tier overridden

Practice approved

Homework deleted

Reflection updated

---

# Score History

Maintain score history.

Students can view:

Current Score

Previous Scores

Improvement Trend

Admin can export history.

---

# Export

Admin can export:

Leaderboard

Score Report

Tier Report

Student Progress

Support:

CSV

Excel (.xlsx)

---

# APIs

Examples:

GET /api/admin/score-config

PUT /api/admin/score-config

GET /api/admin/leaderboard

GET /api/student/leaderboard

GET /api/dashboard/composite

GET /api/dashboard/statistics

GET /api/student/score

GET /api/student/tier

PUT /api/admin/tier/{studentId}

GET /api/admin/analytics

GET /api/admin/export/leaderboard

GET /api/admin/export/scores

---

# Security

Students

Read only their own scores.

Parents

Read-only access to linked student.

Admins

Full access.

---

# Performance

Cache leaderboard.

Recalculate only affected students after updates.

Avoid recalculating all students unnecessarily.

Use transactions where required.

---

# Validation

Weights must equal 100%.

Tier thresholds cannot overlap.

Percentages must remain between 0–100.

Prevent duplicate leaderboard entries.

---

# UI

Continue using the KBV Education design system.

Primary

#1B3A6B

Secondary

#F2F6FA

Accent

#C4972A

Create elegant dashboards with responsive charts.

---

# Out of Scope

Do NOT implement:

Certificates

Google Sheets Sync

Email Automation

SMS

Push Notifications

Claude AI

OpenAI

Speech-to-text

Payment

Public Registration

These will be implemented in Phase 5.

---

# Deliverables

Generate the implementation in small, reviewable steps.

Step 1

Database migrations

Step 2

Entities, repositories, services

Step 3

Score Engine

Step 4

Tier Engine

Step 5

Leaderboard APIs

Step 6

Analytics APIs

Step 7

Admin Configuration UI

Step 8

Student Dashboard Updates

Step 9

Parent Dashboard Updates

Step 10

Leaderboard UI

Step 11

Charts and Analytics

Step 12

Export (CSV and Excel)

Pause after every step and wait for confirmation before continuing.

Maintain complete backward compatibility with Phases 1, 2, and 3.
