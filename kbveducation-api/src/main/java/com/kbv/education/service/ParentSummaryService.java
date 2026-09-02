package com.kbv.education.service;

import com.kbv.education.dto.parent.ParentSummaryResponse;

import java.util.UUID;

public interface ParentSummaryService {

    /** Builds the single Parent screen's weekly summary for the given parent user. */
    ParentSummaryResponse getSummary(UUID parentUserId);
}
