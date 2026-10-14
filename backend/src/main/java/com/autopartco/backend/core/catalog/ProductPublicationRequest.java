package com.autopartco.backend.core.catalog;

import jakarta.validation.constraints.NotNull;

public record ProductPublicationRequest(@NotNull Boolean published) {
}