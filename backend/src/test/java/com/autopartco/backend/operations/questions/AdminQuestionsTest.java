package com.autopartco.backend.operations.questions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import com.autopartco.backend.core.questions.AdminQuestion;
import com.autopartco.backend.core.questions.QuestionModeration;
import com.autopartco.backend.operations.auditlog.AdminAudit;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.web.PageResponse;

import jakarta.validation.Validation;

class AdminQuestionsTest {
    private final QuestionModeration moderation = mock(QuestionModeration.class);
    private final AdminAudit adminAudit = mock(AdminAudit.class);
    private final Authentication root = mock(Authentication.class);
    private final QuestionAdminController controller = new QuestionAdminController(moderation, adminAudit);
    private final UUID questionId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();

    private AdminQuestion question(boolean hidden, String reason) {
        return new AdminQuestion(questionId, "¿Sirve?", "Ana P.", OffsetDateTime.now(), null, storeId, "Repuestos A",
                UUID.randomUUID(), "Filtro", hidden, hidden ? OffsetDateTime.now() : null, reason);
    }

    @Test
    void everyEndpointIsRootOnly() {
        for (Method method : QuestionAdminController.class.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                assertThat(method.getAnnotation(PreAuthorize.class)).as(method.getName()).isNotNull();
                assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('ROLE_ROOT')");
            }
        }
    }

    @Test
    void listingPassesTheHiddenFilter() {
        PageResponse<AdminQuestion> page = PageResponse.of(java.util.List.of(), 1, 20, 0);
        when(moderation.list(true, 2, 10)).thenReturn(page);

        assertThat(controller.list(true, 2, 10)).isSameAs(page);
    }

    @Test
    void hidingIsAuditedWithReasonAndStore() {
        when(moderation.hide(questionId, "Spam")).thenReturn(question(true, "Spam"));

        AdminQuestion result = controller.hide(questionId, new HideQuestionRequest("Spam"), root);

        assertThat(result.hidden()).isTrue();
        verify(adminAudit).record(root, "QUESTION_HIDDEN", "PRODUCT_QUESTION", questionId,
                Map.of("reason", "Spam", "storeId", storeId));
    }

    @Test
    void unhidingIsAudited() {
        when(moderation.unhide(questionId)).thenReturn(question(false, null));

        controller.unhide(questionId, root);

        verify(adminAudit).record(root, "QUESTION_UNHIDDEN", "PRODUCT_QUESTION", questionId,
                Map.of("storeId", storeId));
    }

    @Test
    void failedModerationIsNotAudited() {
        when(moderation.hide(questionId, "Spam"))
                .thenThrow(new ConflictException("QUESTION_ALREADY_HIDDEN", "La pregunta ya está oculta."));

        assertThatThrownBy(() -> controller.hide(questionId, new HideQuestionRequest("Spam"), root))
                .isInstanceOf(ConflictException.class);
        verify(adminAudit, never()).record(eq(root), eq("QUESTION_HIDDEN"), eq("PRODUCT_QUESTION"), eq(questionId),
                anyMap());
    }

    @Test
    void reasonIsValidated() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(new HideQuestionRequest("ab"))).isNotEmpty();
        assertThat(validator.validate(new HideQuestionRequest("x".repeat(201)))).isNotEmpty();
        assertThat(validator.validate(new HideQuestionRequest("Spam"))).isEmpty();
    }
}
