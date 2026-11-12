package com.autopartco.backend.marketplace.questions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.questions.AdminQuestion;
import com.autopartco.backend.core.questions.PortalQuestion;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;

/** Implementaciones de los puertos de portal ({@code StoreQuestionDesk}) y de moderación ({@code QuestionModeration}). */
class StoreQuestionDeskServiceTest {
    private static final String EMAIL = "gerente@almacen.co";

    private final QuestionQueries queries = mock(QuestionQueries.class);
    private final AccountLookup accountLookup = mock(AccountLookup.class);
    private final List<Object> published = new ArrayList<>();
    private final StoreQuestionDeskService desk = new StoreQuestionDeskService(queries, accountLookup, published::add);
    private final QuestionModerationService moderation = new QuestionModerationService(queries);

    private final UUID storeId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-11T15:00:00Z");

    @BeforeEach
    void staff() {
        when(accountLookup.findByEmail(EMAIL)).thenReturn(Optional.of(new AccountRef(accountId, UUID.randomUUID(),
                EMAIL, "Gerente", "ACTIVE", "CUSTOMER", "Gerente")));
    }

    private QuestionQueries.QuestionRow row(String answer, OffsetDateTime hiddenAt) {
        return new QuestionQueries.QuestionRow(questionId, productId, "Filtro", storeId, "Repuestos A",
                UUID.randomUUID(), "¿Sirve para un Mazda 3?", answer, answer == null ? null : now, now, hiddenAt,
                hiddenAt == null ? null : "Spam", "Ana", "Pérez");
    }

    @Test
    void firstAnswerIsSavedWithTheAccountAndNotifiesTheAuthor() {
        when(queries.findInStore(storeId, questionId, null))
                .thenReturn(Optional.of(row(null, null)), Optional.of(row("Sí, sirve.", null)));
        when(queries.setAnswer(storeId, questionId, accountId, "Sí, sirve.")).thenReturn(true);

        PortalQuestion answered = desk.answer(storeId, questionId, null, EMAIL, "  Sí, sirve.  ");

        assertThat(answered.answer().text()).isEqualTo("Sí, sirve.");
        assertThat(answered.author()).isEqualTo("Ana P.");
        assertThat(published).containsExactly(new ProductQuestionAnswered(questionId));
    }

    @Test
    void editingAnAnswerDoesNotNotifyAgain() {
        when(queries.findInStore(storeId, questionId, null)).thenReturn(Optional.of(row("Sí", null)));
        when(queries.setAnswer(storeId, questionId, accountId, "Sí, para 2015 y 2016")).thenReturn(true);

        desk.answer(storeId, questionId, null, EMAIL, "Sí, para 2015 y 2016");

        assertThat(published).isEmpty();
    }

    @Test
    void questionOfAnotherStoreOrOutsideTheBranchesIsNotFound() {
        List<UUID> branches = List.of(branchId);
        when(queries.findInStore(storeId, questionId, branches)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> desk.answer(storeId, questionId, branches, EMAIL, "Sí, sirve."))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("QUESTION_NOT_FOUND"));
        assertThatThrownBy(() -> desk.removeAnswer(storeId, questionId, branches))
                .isInstanceOf(NotFoundException.class);
        verify(queries, never()).setAnswer(any(), any(), any(), anyString());
        verify(queries, never()).clearAnswer(any(), any());
    }

    @Test
    void answersWithContactDataAreRejected() {
        assertThatThrownBy(() -> desk.answer(storeId, questionId, null, EMAIL, "Llámanos al 601 234 5678"))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("QUESTION_CONTACT_NOT_ALLOWED"));
        assertThatThrownBy(() -> desk.answer(storeId, questionId, null, EMAIL, "x".repeat(1001)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("VALIDATION_ERROR"));
        verify(queries, never()).setAnswer(any(), any(), any(), anyString());
    }

    @Test
    void removingTheAnswerClearsIt() {
        when(queries.findInStore(storeId, questionId, null)).thenReturn(Optional.of(row("Sí", null)));
        when(queries.clearAnswer(storeId, questionId)).thenReturn(true);

        desk.removeAnswer(storeId, questionId, null);

        verify(queries).clearAnswer(storeId, questionId);
    }

    @Test
    void pendingCountsVisibleUnansweredQuestionsInScope() {
        List<UUID> branches = List.of(branchId);
        when(queries.count(new QuestionQueries.Filter(storeId, null, false, false, branches))).thenReturn(3L);

        assertThat(desk.pending(storeId, branches)).isEqualTo(3);
    }

    @Test
    void branchScopeOnlyKeepsProductsWithInventoryInThoseBranchesOfTheStore() {
        String scoped = QuestionQueries.listSql(new QuestionQueries.Filter(storeId, null, false, null,
                List.of(branchId)));
        assertThat(scoped).contains("q.store_id = :storeId", "q.answer IS NULL", "FROM inventory i",
                "b.store_id = q.store_id", "i.branch_id IN (:branchIds)", "q.deleted_at IS NULL");
        assertThat(QuestionQueries.listSql(new QuestionQueries.Filter(storeId, null, null, null, List.of())))
                .contains("AND false");
        assertThat(QuestionQueries.listSql(new QuestionQueries.Filter(storeId, null, null, null, null)))
                .doesNotContain("inventory");
    }

    // ------------------------------------------------------------ moderación

    @Test
    void hidingRequiresAReasonAndAVisibleQuestion() {
        assertThatThrownBy(() -> moderation.hide(questionId, " a "))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("QUESTION_REASON_INVALID"));
        when(queries.find(questionId)).thenReturn(Optional.of(row(null, now)));
        assertThatThrownBy(() -> moderation.hide(questionId, "Spam"))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("QUESTION_ALREADY_HIDDEN"));
        verify(queries, never()).hide(any(), any());
    }

    @Test
    void hidingAndUnhiding() {
        when(queries.find(questionId)).thenReturn(Optional.of(row(null, null)), Optional.of(row(null, now)));

        AdminQuestion hidden = moderation.hide(questionId, "  Spam  ");

        verify(queries).hide(questionId, "Spam");
        assertThat(hidden.hidden()).isTrue();
        assertThat(hidden.storeId()).isEqualTo(storeId);

        when(queries.find(questionId)).thenReturn(Optional.of(row(null, now)), Optional.of(row(null, null)));
        assertThat(moderation.unhide(questionId).hidden()).isFalse();
        verify(queries).unhide(questionId);

        when(queries.find(questionId)).thenReturn(Optional.of(row(null, null)));
        assertThatThrownBy(() -> moderation.unhide(questionId))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("QUESTION_NOT_HIDDEN"));
    }

    @Test
    void missingQuestionIsNotFoundForModeration() {
        when(queries.find(questionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moderation.unhide(questionId))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("QUESTION_NOT_FOUND"));
    }
}
