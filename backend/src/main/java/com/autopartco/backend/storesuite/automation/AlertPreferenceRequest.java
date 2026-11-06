package com.autopartco.backend.storesuite.automation;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

/**
 * Elemento del cuerpo del {@code PUT} (lista de hasta 5, mismos campos que devuelve la lectura). {@code inApp}
 * nulo = true y {@code email} nulo = false.
 */
public record AlertPreferenceRequest(@NotBlank String type, Boolean inApp, Boolean email,
                                     Map<String, Object> threshold) {
}
