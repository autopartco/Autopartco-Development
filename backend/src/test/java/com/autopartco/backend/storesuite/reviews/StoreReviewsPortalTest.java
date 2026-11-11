package com.autopartco.backend.storesuite.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PutMapping;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.reviews.PortalReview;
import com.autopartco.backend.core.reviews.ReviewType;
import com.autopartco.backend.core.reviews.StoreReviewDesk;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

import jakarta.validation.Validation;

class StoreReviewsPortalTest {
    private static final String EMAIL = "gerente@almacen.co";

    private final StoreAccess storeAccess = mock(StoreAccess.class);
    private final StoreReviewDesk desk = mock(StoreReviewDesk.class);
    private final StoreReviewsPortal portal = new StoreReviewsPortal(storeAccess, desk);
    private final UUID storeId = UUID.randomUUID();
    private final UUID reviewId = UUID.randomUUID();

    @Test
    void listingRequiresStoreReadAndParsesTheFilters() {
        PageResponse<PortalReview> page = PageResponse.of(List.of(), 1, 20, 0);
        when(desk.list(storeId, ReviewType.PRODUCT, false, 2, 3, 10)).thenReturn(page);

        assertThat(portal.list(storeId, EMAIL, "product", false, 2, 3, 10)).isSameAs(page);

        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.STORE_READ);
    }

    @Test
    void listingWithoutTypeAsksForBothTypes() {
        portal.list(storeId, EMAIL, null, null, null, null, null);

        verify(desk).list(storeId, null, null, null, null, null);
    }

    @Test
    void listingWithoutReadPermissionNeverReachesTheDesk() {
        doThrow(new AccessDeniedException("forbidden")).when(storeAccess)
                .requirePermission(EMAIL, storeId, StorePermissionCodes.STORE_READ);

        assertThatThrownBy(() -> portal.list(storeId, EMAIL, null, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> portal.summary(storeId, EMAIL)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(desk);
    }

    @Test
    void invalidTypeIsRejectedWith400() {
        assertThatThrownBy(() -> portal.list(storeId, EMAIL, "otro", null, null, null, null))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_TYPE_INVALID"));
    }

    @Test
    void replyingRequiresStoreUpdate() {
        doThrow(new AccessDeniedException("forbidden")).when(storeAccess)
                .requirePermission(EMAIL, storeId, StorePermissionCodes.STORE_UPDATE);

        assertThatThrownBy(() -> portal.reply(storeId, EMAIL, "store", reviewId, "Gracias"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> portal.removeReply(storeId, EMAIL, "store", reviewId))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(desk);
    }

    @Test
    void replyDelegatesWithTheAccountAndType() {
        portal.reply(storeId, EMAIL, "product", reviewId, "Gracias por tu compra");

        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.STORE_UPDATE);
        verify(desk).reply(storeId, ReviewType.PRODUCT, reviewId, EMAIL, "Gracias por tu compra");
    }

    @Test
    void reviewOfAnotherStoreIsNotFound() {
        when(desk.reply(any(), any(), any(), any(), any()))
                .thenThrow(new NotFoundException("REVIEW_NOT_FOUND", "No se encontró la reseña."));

        assertThatThrownBy(() -> portal.reply(storeId, EMAIL, "store", reviewId, "Gracias"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_NOT_FOUND"));
    }

    @Test
    void removingTheReplyRequiresStoreUpdateAndDelegates() {
        portal.removeReply(storeId, EMAIL, "store", reviewId);

        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.STORE_UPDATE);
        verify(desk).removeReply(storeId, ReviewType.STORE, reviewId);
    }

    @Test
    void summaryRequiresStoreRead() {
        portal.summary(storeId, EMAIL);

        verify(storeAccess).requirePermission(EMAIL, storeId, StorePermissionCodes.STORE_READ);
        verify(desk).summary(storeId);
    }

    @Test
    void replyTextIsRequiredAndLimitedTo1000Characters() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(new ReviewReplyRequest("Gracias"))).isEmpty();
        assertThat(validator.validate(new ReviewReplyRequest("  "))).hasSize(1);
        assertThat(validator.validate(new ReviewReplyRequest(null))).hasSize(1);
        assertThat(validator.validate(new ReviewReplyRequest("a".repeat(1001)))).hasSize(1);
        assertThat(validator.validate(new ReviewReplyRequest("a".repeat(1000)))).isEmpty();
    }

    @Test
    void controllerRoutesMatchTheContract() throws NoSuchMethodException {
        Method reply = StoreReviewsController.class.getMethod("reply", UUID.class, String.class, UUID.class,
                ReviewReplyRequest.class, org.springframework.security.core.Authentication.class);

        assertThat(reply.getAnnotation(PutMapping.class).value()).containsExactly("/{type}/{reviewId}/reply");
    }
}
