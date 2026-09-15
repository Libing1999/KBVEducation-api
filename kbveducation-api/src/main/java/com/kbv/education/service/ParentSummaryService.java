package com.kbv.education.service;

import com.kbv.education.dto.parent.ParentChildResponse;
import com.kbv.education.dto.parent.ParentSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface ParentSummaryService {

    /**
     * Builds the single Parent screen's weekly summary for the given parent user.
     * {@code requestedStudentId} selects which child for a multi-child parent; null
     * defaults to their first-linked child.
     */
    ParentSummaryResponse getSummary(UUID parentUserId, UUID requestedStudentId);

    /** All children linked to this parent, oldest-linked first. */
    List<ParentChildResponse> listMyChildren(UUID parentUserId);
}
