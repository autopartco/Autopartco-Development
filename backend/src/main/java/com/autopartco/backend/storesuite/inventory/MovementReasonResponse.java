package com.autopartco.backend.storesuite.inventory;

/** Motivo de ajuste: {@code direction} es {@code IN}, {@code OUT} o {@code ANY}. */
public record MovementReasonResponse(String code, String label, String direction) {
}
