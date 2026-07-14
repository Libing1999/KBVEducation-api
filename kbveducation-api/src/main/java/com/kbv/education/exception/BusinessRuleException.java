package com.kbv.education.exception;

/**
 * Thrown when a domain invariant is violated (e.g. assigning a student to a
 * full cohort). Maps to HTTP 422.
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
