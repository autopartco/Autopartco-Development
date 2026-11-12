package com.autopartco.backend.operations.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import com.autopartco.backend.core.reviews.AdminReview;
import com.autopartco.backend.core.reviews.ReviewModeration;
import com.autopartco.backend.core.reviews.ReviewType;
import com.autopartco.backend.operations.auditlog.AdminAudit;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;

import jakarta.validation.Validation;

class AdminReviewsTest {
    private final ReviewModeration moderation = mock(ReviewModeration.class);
    private final AdminAudit adminAudit = mock(AdminAudit.class);
    private final Authentication root = mock(Authentication.class);
    private final ReviewAdminController controller = new ReviewAdminController(moderation, adminAudit);
    private final UUID reviewId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();

    private AdminReview review(ReviewType type, boolean hidden, String reason) {
        return new AdminReview(reviewId, type, 1, "Malo", "Ana P.", OffsetDateTime.now(), null, storeId, "Autopartes",
                null, null, hidden, hidden ? OffsetDateTime.now() : null, reason);
    }

    @Test
    void everyEndpointIsRootOnly() {
        for (Method method : ReviewAdminController.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                assertThat(method.getAnnotation(PreAuthorize.class)).as(method.getName()).isNotNull();
                assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('ROLE_ROOT')");
            }
        }
    }

    @Test
    void hidingIsAuditedWithReasonAndStore() {
        when(moderation.hide(ReviewType.PRODUCT, reviewId, "Insultos"))
                .thenReturn(review(ReviewType.PRODUCT, true, "Insultos"));

        AdminReview result = controller.hide("product", reviewId, new HideReviewRequest("Insultos"), root);

        assertThat(result.hidden()).isTrue();
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(adminAudit).record(eq(root), eq("REVIEW_HIDDEN"), eq("PRODUCT_REVIEW"), eq(reviewId), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("reason", "Insultos").containsEntry("storeId", storeId);
    }

    @Test
    void unhidingIsAudited() {
        when(moderation.unhide(ReviewType.STORE, reviewId)).thenReturn(review(ReviewType.STORE, false, null));

        controller.unhide("store", reviewId, root);

        verify(adminAudit).record(eq(root), eq("REVIEW_UNHIDDEN"), eq("STORE_REVIEW"), eq(reviewId), anyMap());
    }

    @Test
    void failedModerationIsNotAudited() {
        when(moderation.hide(ReviewType.STORE, reviewId, "Spam"))
                .thenThrow(new ConflictException("REVIEW_ALREADY_HIDDEN", "La reseña ya está oculta."));

        assertThatThrownBy(() -> controller.hide("store", reviewId, new HideReviewRequest("Spam"), root))
                .isInstanceOf(ConflictException.class);
        verify(adminAudit, never()).record(eq(root), eq("REVIEW_HIDDEN"), eq("STORE_REVIEW"), eq(reviewId), anyMap());
    }

    @Test
    void unknownTypeIsRejected() {
        assertThatThrownBy(() -> controller.unhide("otro", reviewId, root))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("REVIEW_TYPE_INVALID"));
    }

    @Test
    void listParsesTheFilters() {
        controller.list(true, "product", 2, 10);
        controller.list(null, null, null, null);

        verify(moderation).list(true, ReviewType.PRODUCT, 2, 10);
        verify(moderation).list(null, null, null, null);
    }

    @Test
    void reasonMustHaveBetween3And200Characters() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(new HideReviewRequest("ab"))).hasSize(1);
        assertThat(validator.validate(new HideReviewRequest("abc"))).isEmpty();
        assertThat(validator.validate(new HideReviewRequest("a".repeat(200)))).isEmpty();
        assertThat(validator.validate(new HideReviewRequest("a".repeat(201)))).hasSize(1);
        assertThat(validator.validate(new HideReviewRequest("   "))).isNotEmpty();
    }
}
