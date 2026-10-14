package com.autopartco.backend.core.catalog;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductImageOrderRequest(
        @NotEmpty @Size(max = 10) List<@NotNull UUID> imageIds
) {
}
