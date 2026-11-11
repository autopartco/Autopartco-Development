package com.autopartco.backend.core.advertising;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta o edición de una campaña en {@code DRAFT}. El precio nunca viaja: lo calcula el servidor. Los topes de
 * negocio (1 a {@value AdCampaignRules#MAX_PRODUCTS} productos, fechas) se validan en el servicio para responder con
 * sus códigos propios ({@code AD_TOO_MANY_PRODUCTS}, {@code AD_INVALID_DATES}); aquí solo la forma.
 */
public record AdCampaignRequest(
        @NotBlank @Size(max = 80) String name,
        @NotEmpty List<@NotNull UUID> productIds,
        @NotEmpty @Size(max = 4) List<@NotNull AdPlacement> placements,
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        @Valid AdTargeting targeting
) {
}
