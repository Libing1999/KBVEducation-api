package com.kbv.education.dto.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Generic reorder payload: new display order for a set of entities. */
public record ReorderRequest(
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotNull UUID id,
            int displayOrder
    ) {
    }
}
