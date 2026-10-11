package com.autopartco.backend.seller.dto;

import jakarta.validation.constraints.Size;

public record SellerApplicationReviewRequest(
        boolean approved,
        @Size(max = 2000) String notes
) {
}
