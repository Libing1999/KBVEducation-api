package com.kbv.education.dto.parent;

import java.util.UUID;

/** One of a parent's linked children, for the Parent screen's child selector. */
public record ParentChildResponse(UUID id, String firstName, String lastName) {
}
