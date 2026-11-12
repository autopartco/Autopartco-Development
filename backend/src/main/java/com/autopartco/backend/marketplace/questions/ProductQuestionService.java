package com.autopartco.backend.marketplace.questions;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Preguntas en la ficha de producto. Pregunta cualquier comprador con sesión (sin haberlo comprado); el almacén es el
 * del producto. El público ve solo las respondidas; el autor ve además las suyas pendientes ({@code mine:true}).
 * Ocultas y borradas no salen nunca. El autor borra la suya mientras no tenga respuesta. Preguntar publica
 * {@link ProductQuestionAsked} (avisos tras el commit).
 */
@Service
@AllArgsConstructor
public class ProductQuestionService {
    static final int DEFAULT_SIZE = 10;
    static final int MAX_SIZE = 50;
    static final int MAX_PAGE = 1_000;

    private final QuestionQueries queries;
    private final AccountLookup accountLookup;
    private final ApplicationEventPublisher events;
    private final QuestionWriteLimiter limiter;

    /**
     * Respondidas primero (las más recientes), luego las pendientes del autor. 404 {@code PRODUCT_NOT_FOUND}; 400
     * {@code QUESTION_QUERY_INVALID} si {@code q} pasa de 80 caracteres.
     *
     * @param email cuenta que consulta o {@code null} sin sesión
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductQuestionResponse> list(UUID productId, Integer page, Integer size, String q,
            String email) {
        String pattern = QuestionRules.searchPattern(q);
        requireStore(productId);
        int p = page == null ? 1 : Math.min(MAX_PAGE, Math.max(1, page));
        int s = size == null ? DEFAULT_SIZE : Math.min(MAX_SIZE, Math.max(1, size));
        UUID viewer = email == null ? null : accountLookup.findByEmail(email).map(AccountRef::personId).orElse(null);
        List<ProductQuestionResponse> items = queries.publicList(productId, viewer, pattern, s, (p - 1) * s).stream()
                .map(row -> ProductQuestionResponse.of(row, viewer))
                .toList();
        return PageResponse.of(items, p, s, queries.publicCount(productId, viewer, pattern));
    }

    /**
     * 404 {@code PRODUCT_NOT_FOUND}; 400 {@code QUESTION_INVALID}/{@code QUESTION_CONTACT_NOT_ALLOWED}; 429
     * {@code QUESTION_RATE_LIMITED} (5 por hora por cuenta; las rechazadas por validación no cuentan).
     */
    @Transactional
    public ProductQuestionResponse ask(UUID productId, String email, ProductQuestionRequest request) {
        UUID storeId = requireStore(productId);
        AccountRef account = account(email);
        String question = QuestionRules.question(request == null ? null : request.question());
        limiter.acquire(account.accountId());
        UUID questionId = queries.insert(productId, storeId, account.personId(), question);
        events.publishEvent(new ProductQuestionAsked(questionId));
        QuestionQueries.QuestionRow row = queries.find(questionId).orElseThrow(ProductQuestionService::notFound);
        return ProductQuestionResponse.of(row, account.personId());
    }

    /**
     * Solo el autor y solo sin respuesta. Ajena, de otro producto o inexistente: 404 {@code QUESTION_NOT_FOUND};
     * respondida: 409 {@code QUESTION_ALREADY_ANSWERED}.
     */
    @Transactional
    public void delete(UUID productId, UUID questionId, String email) {
        AccountRef account = account(email);
        QuestionQueries.QuestionRow row = queries.find(questionId)
                .filter(found -> found.productId().equals(productId) && found.personId().equals(account.personId()))
                .orElseThrow(ProductQuestionService::notFound);
        if (row.answered() || !queries.softDeleteUnanswered(questionId, account.personId())) {
            throw new ConflictException("QUESTION_ALREADY_ANSWERED",
                    "La pregunta ya tiene respuesta y no se puede borrar.");
        }
    }

    private UUID requireStore(UUID productId) {
        return queries.productStore(productId)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "No se encontró el repuesto."));
    }

    private AccountRef account(String email) {
        return accountLookup.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta autenticada."));
    }

    static NotFoundException notFound() {
        return new NotFoundException("QUESTION_NOT_FOUND", "No se encontró la pregunta.");
    }
}
