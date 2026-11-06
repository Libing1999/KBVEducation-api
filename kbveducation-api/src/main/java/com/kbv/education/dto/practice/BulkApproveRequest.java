package com.kbv.education.dto.practice;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Approve several sessions at once (e.g. "select all green"). Only sessions still waiting are approved. */
public record BulkApproveRequest(
        @NotEmpty @Size(max = 500) List<UUID> ids
) {
}
