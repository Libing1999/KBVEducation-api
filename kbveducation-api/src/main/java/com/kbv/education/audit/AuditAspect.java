package com.kbv.education.audit;

import com.kbv.education.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Records one {@code audit_logs} row per {@link Audited}-annotated method call.
 * The actor is normally {@code AuditLog}'s own {@code createdBy} (populated by
 * the same JPA auditing every entity already uses) — this aspect only fills in
 * what JPA auditing can't: the entity touched, a best-effort "new value"
 * summary, and (for auth actions, where the caller isn't authenticated yet)
 * the attempted email. IP/User-Agent come from {@link RequestContextHolder},
 * which only works on the original request thread — never call an audited
 * method from an async job or {@code @Scheduled} task expecting these to
 * populate.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogService auditLogService;

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        try {
            Object result = joinPoint.proceed();
            record(audited.action(), audited.entityType(), joinPoint.getArgs(), result, audited.captureResult());
            return result;
        } catch (Throwable ex) {
            if (!audited.failureAction().isBlank()) {
                record(audited.failureAction(), audited.entityType(), joinPoint.getArgs(), null, false);
            }
            throw ex;
        }
    }

    private void record(String action, String entityType, Object[] args, Object result, boolean captureResult) {
        UUID entityId = extractUuid(args, result);
        String email = extractEmail(args, result);
        String newValue = (result == null || !captureResult) ? null : String.valueOf(result);
        auditLogService.record(action, entityType, entityId, email, null, newValue,
                currentIp(), currentUserAgent());
    }

    private UUID extractUuid(Object[] args, Object result) {
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid;
            }
        }
        return tryInvoke(result, UUID.class, "id", "getId");
    }

    private String extractEmail(Object[] args, Object result) {
        for (Object arg : args) {
            String email = tryInvoke(arg, String.class, "email", "getEmail");
            if (email != null) {
                return email;
            }
        }
        return tryInvoke(result, String.class, "email", "getEmail");
    }

    @SuppressWarnings("unchecked")
    private <T> T tryInvoke(Object target, Class<T> returnType, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String name : methodNames) {
            try {
                Method method = target.getClass().getMethod(name);
                Object value = method.invoke(target);
                if (returnType.isInstance(value)) {
                    return (T) value;
                }
            } catch (ReflectiveOperationException ignored) {
                // Not every audited method's args/return type has this accessor - expected, not an error.
            }
        }
        return null;
    }

    private String currentIp() {
        ServletRequestAttributes attrs = currentRequest();
        if (attrs == null) {
            return null;
        }
        String forwarded = attrs.getRequest().getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return attrs.getRequest().getRemoteAddr();
    }

    private String currentUserAgent() {
        ServletRequestAttributes attrs = currentRequest();
        if (attrs == null) {
            return null;
        }
        String userAgent = attrs.getRequest().getHeader("User-Agent");
        return userAgent != null && userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent;
    }

    private ServletRequestAttributes currentRequest() {
        try {
            return (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
