package com.autopartco.backend.core.organization;

/** Qué ofrece el almacén (columna {@code store.business_type}): repuestos, servicios o ambos. */
public enum BusinessType {
    PARTS, SERVICES, BOTH;

    /** {@code PARTS} si no se indica. */
    public static BusinessType orDefault(BusinessType value) {
        return value == null ? PARTS : value;
    }

    /** El perfil exige productos publicados (un taller {@code SERVICES} puede operar sin ellos). */
    public boolean requiresProducts() {
        return this != SERVICES;
    }
}
