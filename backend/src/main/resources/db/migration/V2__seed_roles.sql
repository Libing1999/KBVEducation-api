-- ============================================================================
-- Seed the three Phase-1 roles. Idempotent: safe if names already exist.
-- The SUPER_ADMIN *user* is seeded at application startup (Step 4) so the
-- password can be BCrypt-hashed by the application's PasswordEncoder.
-- ============================================================================

INSERT INTO roles (id, name, description)
VALUES
    (gen_random_uuid(), 'SUPER_ADMIN', 'Platform administrator with full access'),
    (gen_random_uuid(), 'STUDENT',     'Enrolled student'),
    (gen_random_uuid(), 'PARENT',      'Parent linked to a student')
ON CONFLICT (name) DO NOTHING;
