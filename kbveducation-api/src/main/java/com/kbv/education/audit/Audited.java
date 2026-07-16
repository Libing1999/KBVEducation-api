package com.kbv.education.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for the general-purpose audit trail (Phase 5 Step 4).
 * {@link AuditAspect} records one {@code audit_logs} row when the method
 * returns successfully, and — if {@link #failureAction()} is set — another
 * when it throws. This is applied to a deliberately explicit, stated set of
 * existing write paths (see the class list in the implementation plan), not
 * every write path in the app; new Phase 5 actions get it natively at their
 * call site instead.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    String action();

    String entityType();

    /** If set, a second entry is recorded under this action when the method throws (e.g. LOGIN_FAILED). */
    String failureAction() default "";

    /**
     * Whether to store a best-effort {@code toString()} of the return value as
     * {@code newValue}. Set to {@code false} for methods whose result carries a
     * credential (e.g. login returns an access token) — that must never land
     * in a database column the audit trail displays back to admins.
     */
    boolean captureResult() default true;
}
