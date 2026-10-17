package com.autopartco.backend.core.catalog;

import java.util.UUID;

public record ManufacturerResponse(UUID id, String name, boolean approved, UUID suggestedByStoreId) {
}
