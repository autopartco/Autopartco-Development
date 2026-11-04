package com.autopartco.backend.core.catalog;

import java.util.UUID;

public record ManufacturerSearchResult(UUID id, String name, boolean approved, double similarity) {
}
