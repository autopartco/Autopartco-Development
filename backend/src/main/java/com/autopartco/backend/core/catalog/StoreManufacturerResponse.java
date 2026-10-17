package com.autopartco.backend.core.catalog;

import java.util.UUID;

public record StoreManufacturerResponse(UUID id, String name, boolean approved) {
}
