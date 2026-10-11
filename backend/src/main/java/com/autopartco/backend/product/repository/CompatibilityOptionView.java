package com.autopartco.backend.product.repository;

import java.util.UUID;

public interface CompatibilityOptionView {
    UUID getId();

    String getMake();

    String getModel();

    String getVersion();
}