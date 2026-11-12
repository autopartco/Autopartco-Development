package com.autopartco.backend.marketplace.questions;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.questions.PortalQuestion;
import com.autopartco.backend.core.questions.QuestionAnswerView;
import com.autopartco.backend.core.questions.StoreQuestionDesk;
import com.autopartco.backend.marketplace.reviews.StoreReviewService;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Portal del almacén: lista las preguntas sobre sus productos (con las ocultas marcadas), cuenta las pendientes y
 * responde. La primera respuesta publica {@link ProductQuestionAnswered} (aviso al autor tras el commit).
 */
@Service
@AllArgsConstructor
public class StoreQuestionDeskService implements StoreQuestionDesk {
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final QuestionQueries queries;
    private final AccountLookup accountLookup;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PortalQuestion> list(UUID storeId, Boolean answered, Collection<UUID> branchIds, Integer page,
            Integer size) {
        int p = page == null ? 1 : Math.min(ProductQuestionService.MAX_PAGE, Math.max(1, page));
        int s = size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));
        QuestionQueries.Filter filter = new QuestionQueries.Filter(storeId, null, answered, null, branchIds);
        List<PortalQuestion> items = queries.list(filter, s, (p - 1) * s).stream()
                .map(StoreQuestionDeskService::toPortal)
                .toList();
        return PageResponse.of(items, p, s, queries.count(filter));
    }

    @Override
    @Transactional(readOnly = true)
    public long pending(UUID storeId, Collection<UUID> branchIds) {
        return queries.count(new QuestionQueries.Filter(storeId, null, false, false, branchIds));
    }

    @Override
    @Transactional
    public PortalQuestion answer(UUID storeId, UUID questionId, Collection<UUID> branchIds, String email,
            String text) {
        String answer = QuestionRules.answer(text);
        AccountRef account = accountLookup.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta autenticada."));
        QuestionQueries.QuestionRow before = require(storeId, questionId, branchIds);
        if (!queries.setAnswer(storeId, questionId, account.accountId(), answer)) {
            throw ProductQuestionService.notFound();
        }
        if (!before.answered()) {
            events.publishEvent(new ProductQuestionAnswered(questionId));
        }
        return toPortal(require(storeId, questionId, branchIds));
    }

    @Override
    @Transactional
    public void removeAnswer(UUID storeId, UUID questionId, Collection<UUID> branchIds) {
        require(storeId, questionId, branchIds);
        if (!queries.clearAnswer(storeId, questionId)) {
            throw ProductQuestionService.notFound();
        }
    }

    private QuestionQueries.QuestionRow require(UUID storeId, UUID questionId, Collection<UUID> branchIds) {
        return queries.findInStore(storeId, questionId, branchIds).orElseThrow(ProductQuestionService::notFound);
    }

    static PortalQuestion toPortal(QuestionQueries.QuestionRow row) {
        return new PortalQuestion(row.id(), row.question(), StoreReviewService.authorName(row.name(), row.surname()),
                row.createdAt(), row.answer() == null ? null : new QuestionAnswerView(row.answer(), row.answeredAt()),
                row.hidden(), row.productId(), row.productName());
    }
}
