package com.autopartco.backend.core.vehicle.internal;

import java.util.UUID;

/** Modelo o versión con el id de su padre (marca o modelo). */
public interface VehicleRefView {
    UUID getId();

    UUID getParentId();

    String getName();
}
