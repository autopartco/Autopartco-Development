package com.autopartco.backend.marketplace.questions;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.questions.AdminQuestion;
import com.autopartco.backend.core.questions.QuestionAnswerView;
import com.autopartco.backend.core.questions.QuestionModeration;
import com.autopartco.backend.marketplace.reviews.StoreReviewService;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/** Moderación por la plataforma. Una pregunta oculta deja de verse en la ficha (también para su autor). */
@Service
@AllArgsConstructor
public class QuestionModerationService implements QuestionModeration {
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    static final int REASON_MIN = 3;
    static final int REASON_MAX = 200;

    private final QuestionQueries queries;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminQuestion> list(Boolean hidden, Integer page, Integer size) {
        int p = page == null ? 1 : Math.min(ProductQuestionService.MAX_PAGE, Math.max(1, page));
        int s = size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));
        QuestionQueries.Filter filter = new QuestionQueries.Filter(null, null, null, hidden, null);
        List<AdminQuestion> items = queries.list(filter, s, (p - 1) * s).stream()
                .map(QuestionModerationService::toAdmin)
                .toList();
        return PageResponse.of(items, p, s, queries.count(filter));
    }

    @Override
    @Transactional
    public AdminQuestion hide(UUID questionId, String reason) {
        String clean = reason == null ? "" : reason.strip();
        if (clean.length() < REASON_MIN || clean.length() > REASON_MAX) {
            throw new BadRequestException("QUESTION_REASON_INVALID",
                    "El motivo debe tener entre " + REASON_MIN + " y " + REASON_MAX + " caracteres.");
        }
        if (require(questionId).hidden()) {
            throw new ConflictException("QUESTION_ALREADY_HIDDEN", "La pregunta ya está oculta.");
        }
        queries.hide(questionId, clean);
        return toAdmin(require(questionId));
    }

    @Override
    @Transactional
    public AdminQuestion unhide(UUID questionId) {
        if (!require(questionId).hidden()) {
            throw new ConflictException("QUESTION_NOT_HIDDEN", "La pregunta no está oculta.");
        }
        queries.unhide(questionId);
        return toAdmin(require(questionId));
    }

    private QuestionQueries.QuestionRow require(UUID questionId) {
        return queries.find(questionId).orElseThrow(ProductQuestionService::notFound);
    }

    static AdminQuestion toAdmin(QuestionQueries.QuestionRow row) {
        return new AdminQuestion(row.id(), row.question(), StoreReviewService.authorName(row.name(), row.surname()),
                row.createdAt(), row.answer() == null ? null : new QuestionAnswerView(row.answer(), row.answeredAt()),
                row.storeId(), row.storeName(), row.productId(), row.productName(), row.hidden(), row.hiddenAt(),
                row.hiddenReason());
    }
}
