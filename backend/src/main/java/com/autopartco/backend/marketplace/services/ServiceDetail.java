package com.autopartco.backend.marketplace.services;

import java.util.List;

import com.autopartco.backend.core.organization.PublicBranches.PublicBranch;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * Detalle público de un servicio: la tarjeta con sus campos al mismo nivel y lo propio del detalle. {@code branches}
 * son solo las sucursales activas que lo prestan (mismo formato que {@code /stores/{id}/branches}); {@code related}
 * hasta {@value ServiceBrowseController#RELATED_LIMIT} servicios de la misma categoría, cerca.
 * {@code whatsappServiceMessage} es la plantilla del almacén (admite {@code {servicio}}, {@code {sucursal}} y
 * {@code {enlace}}); con {@code whatsappEnabled=false} ninguna sucursal expone WhatsApp.
 */
public record ServiceDetail(
        @JsonUnwrapped ServiceCard card,
        String description,
        List<PublicBranch> branches,
        boolean whatsappEnabled,
        String whatsappServiceMessage,
        List<ServiceCard> related
) {
}
