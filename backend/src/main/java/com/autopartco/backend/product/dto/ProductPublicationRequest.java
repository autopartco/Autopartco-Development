package com.autopartco.backend.product.dto;

import jakarta.validation.constraints.NotNull;

public record ProductPublicationRequest(@NotNull Boolean published) {
}