package com.autopartco.backend.core.vehicle.internal;

import java.util.UUID;

public interface CompatibilityOptionView {
    UUID getId();

    String getMake();

    String getModel();

    String getVersion();
}