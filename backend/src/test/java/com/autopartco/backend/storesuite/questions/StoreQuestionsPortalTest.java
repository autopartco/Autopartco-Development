package com.autopartco.backend.storesuite.questions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.questions.PortalQuestion;
import com.autopartco.backend.core.questions.StoreQuestionDesk;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

import jakarta.validation.Validation;

class StoreQuestionsPortalTest {
    private static final String EMAIL = "colaborador@almacen.co";

    private final StoreAccess storeAccess = mock(StoreAccess.class);
    private final StoreQuestionDesk desk = mock(StoreQuestionDesk.class);
    private final StoreQuestionsPortal portal = new StoreQuestionsPortal(storeAccess, desk);
    private final UUID storeId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final UUID branchA = UUID.randomUUID();
    private final UUID branchB = UUID.randomUUID();

    @Test
    void storeReadSeesTheWholeStoreOrTheChosenBranch() {
        when(storeAccess.hasPermission(EMAIL, storeId, StorePermissionCodes.STORE_READ)).thenReturn(true);
        PageResponse<PortalQuestion> page = PageResponse.of(List.of(), 1, 20, 0);
        when(desk.list(storeId, false, null, 2, 10)).thenReturn(page);

        assertThat(portal.list(storeId, EMAIL, false, null, 2, 10)).isSameAs(page);
        portal.list(storeId, EMAIL, null, branchB, null, null);

        verify(desk).list(storeId, null, List.of(branchB), null, null);
    }

    @Test
    void branchCollaboratorWithoutStoreReadOnlySeesTheirBranches() {
        when(storeAccess.assignedBranches(EMAIL, storeId)).thenReturn(List.of(branchA));

        portal.list(storeId, EMAIL, null, null, null, null);
        portal.list(storeId, EMAIL, true, branchA, null, null);

        verify(desk).list(storeId, null, List.of(branchA), null, null);
        verify(desk).list(storeId, true, List.of(branchA), null, null);
        assertThatThrownBy(() -> portal.list(storeId, EMAIL, null, branchB, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void withoutPermissionOrBranchesEverythingIsForbidden() {
        when(storeAccess.assignedBranches(EMAIL, storeId)).thenReturn(List.of());

        assertThatThrownBy(() -> portal.list(storeId, EMAIL, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> portal.summary(storeId, EMAIL)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> portal.answer(storeId, EMAIL, questionId, "Sí"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> portal.removeAnswer(storeId, EMAIL, questionId))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(desk);
    }

    @Test
    void storeUpdateAnswersAnyQuestionOfTheStore() {
        when(storeAccess.hasPermission(EMAIL, storeId, StorePermissionCodes.STORE_UPDATE)).thenReturn(true);

        portal.answer(storeId, EMAIL, questionId, "Sí, sirve.");
        portal.removeAnswer(storeId, EMAIL, questionId);

        verify(desk).answer(storeId, questionId, null, EMAIL, "Sí, sirve.");
        verify(desk).removeAnswer(storeId, questionId, null);
    }

    @Test
    void branchCollaboratorAnswersWithinTheirBranches() {
        when(storeAccess.assignedBranches(EMAIL, storeId)).thenReturn(List.of(branchA));

        portal.answer(storeId, EMAIL, questionId, "Sí, sirve.");

        verify(desk).answer(storeId, questionId, List.of(branchA), EMAIL, "Sí, sirve.");
    }

    @Test
    void questionOfAnotherStoreIsNotFound() {
        when(storeAccess.hasPermission(EMAIL, storeId, StorePermissionCodes.STORE_UPDATE)).thenReturn(true);
        when(desk.answer(storeId, questionId, null, EMAIL, "Sí"))
                .thenThrow(new NotFoundException("QUESTION_NOT_FOUND", "No se encontró la pregunta."));

        assertThatThrownBy(() -> portal.answer(storeId, EMAIL, questionId, "Sí"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("QUESTION_NOT_FOUND"));
    }

    @Test
    void summaryCountsPendingWithinTheScope() {
        when(storeAccess.hasPermission(EMAIL, storeId, StorePermissionCodes.STORE_READ)).thenReturn(true);
        when(desk.pending(storeId, null)).thenReturn(4L);

        assertThat(portal.summary(storeId, EMAIL)).isEqualTo(new QuestionSummary(4));
    }

    @Test
    void readPermissionDoesNotAllowAnsweringOutsideTheBranches() {
        when(storeAccess.hasPermission(EMAIL, storeId, StorePermissionCodes.STORE_READ)).thenReturn(true);
        when(storeAccess.assignedBranches(EMAIL, storeId)).thenReturn(List.of());

        assertThatThrownBy(() -> portal.answer(storeId, EMAIL, questionId, "Sí"))
                .isInstanceOf(AccessDeniedException.class);
        verify(desk, org.mockito.Mockito.never()).answer(any(), any(), any(), any(), any());
    }

    @Test
    void answerRequestIsValidated() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(new QuestionAnswerRequest(" "))).isNotEmpty();
        assertThat(validator.validate(new QuestionAnswerRequest("x".repeat(1001)))).isNotEmpty();
        assertThat(validator.validate(new QuestionAnswerRequest("Sí, sirve."))).isEmpty();
    }
}
