package com.autopartco.backend.shared.events;

/**
 * Evento de dominio: cambiaron las opciones públicas del catálogo (categorías o fabricantes aprobados). Tras el commit
 * vacía la caché {@code catalog:options}.
 */
public record CatalogOptionsChanged() {
}
