package com.autopartco.backend.marketplace.questions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.TooManyRequestsException;
import com.autopartco.backend.shared.web.PageResponse;

class ProductQuestionServiceTest {
    private static final String EMAIL = "ana@correo.co";
    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");

    private final QuestionQueries queries = mock(QuestionQueries.class);
    private final AccountLookup accountLookup = mock(AccountLookup.class);
    private final List<Object> published = new ArrayList<>();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final ProductQuestionService service = new ProductQuestionService(queries, accountLookup, published::add,
            QuestionWriteLimiter.inMemory(clock));

    private final UUID productId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();
    private final UUID personId = UUID.randomUUID();
    private final UUID otherPerson = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @BeforeEach
    void buyerAndProduct() {
        when(accountLookup.findByEmail(EMAIL)).thenReturn(Optional.of(new AccountRef(UUID.randomUUID(), personId,
                EMAIL, "Ana María Pérez Gómez", "ACTIVE", "CUSTOMER", "Ana María")));
        when(queries.productStore(productId)).thenReturn(Optional.of(storeId));
    }

    private QuestionQueries.QuestionRow row(UUID id, UUID author, String answer) {
        return new QuestionQueries.QuestionRow(id, productId, "Filtro de aceite", storeId, "Repuestos A", author,
                "¿Sirve para un Mazda 3 modelo 2015?", answer, answer == null ? null : now, now, null, null,
                "Ana María", "Pérez Gómez");
    }

    // ------------------------------------------------------------ preguntar

    @Test
    void buyerAsksAndTheStoreIsTakenFromTheProduct() {
        when(queries.insert(productId, storeId, personId, "¿Sirve para un Mazda 3 modelo 2015?"))
                .thenReturn(questionId);
        when(queries.find(questionId)).thenReturn(Optional.of(row(questionId, personId, null)));

        ProductQuestionResponse response = service.ask(productId, EMAIL,
                new ProductQuestionRequest("  ¿Sirve para un Mazda 3 modelo 2015?  "));

        assertThat(response.id()).isEqualTo(questionId);
        assertThat(response.mine()).isTrue();
        assertThat(response.answer()).isNull();
        assertThat(response.author()).isEqualTo("Ana P.");
        assertThat(published).containsExactly(new ProductQuestionAsked(questionId));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Llámame al 300 123 4567 por favor",
            "Mi número es 3001234567, escríbeme",
            "Escríbeme al 311-555-1234 hoy",
            "Contáctame +57 para hablar del repuesto",
            "Mi fijo es 601 234 5678 en Bogotá",
            "Escríbeme a ana.perez@correo.com por favor",
            "Mira esta oferta en www.otrositio.co ahora",
            "Más info en https://ejemplo.com/repuesto"
    })
    void contactDataIsRejected(String text) {
        assertThatThrownBy(() -> service.ask(productId, EMAIL, new ProductQuestionRequest(text)))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("QUESTION_CONTACT_NOT_ALLOWED"));
        verify(queries, never()).insert(any(), any(), any(), any());
        assertThat(published).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "¿Sirve para un Mazda 3 del 2015?",
            "¿El precio de 1.250.000 incluye la instalación?",
            "¿Viene con la referencia 04465 original?"
    })
    void ordinaryQuestionsAreAccepted(String text) {
        assertThat(QuestionRules.question(text)).isEqualTo(text);
    }

    @Test
    void tooShortOrTooLongQuestionsAreInvalid() {
        for (String text : new String[] {null, "   ", "¿Sirve?", "x".repeat(301)}) {
            assertThatThrownBy(() -> service.ask(productId, EMAIL, new ProductQuestionRequest(text)))
                    .isInstanceOfSatisfying(BadRequestException.class,
                            error -> assertThat(error.getCode()).isEqualTo("QUESTION_INVALID"));
        }
        assertThat(QuestionRules.question("x".repeat(300))).hasSize(300);
    }

    @Test
    void sixthQuestionInAnHourIsRateLimited() {
        when(queries.insert(any(), any(), any(), any())).thenReturn(questionId);
        when(queries.find(questionId)).thenReturn(Optional.of(row(questionId, personId, null)));
        for (int i = 0; i < 5; i++) {
            service.ask(productId, EMAIL, new ProductQuestionRequest("¿Tienen disponible el repuesto? " + i));
        }

        assertThatThrownBy(() -> service.ask(productId, EMAIL,
                new ProductQuestionRequest("¿Tienen disponible el repuesto otra vez?")))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        error -> assertThat(error.getCode()).isEqualTo("QUESTION_RATE_LIMITED"));
    }

    @Test
    void askingAboutAMissingProductIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(queries.productStore(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ask(missing, EMAIL, new ProductQuestionRequest("¿Tienen este repuesto?")))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("PRODUCT_NOT_FOUND"));
    }

    // ------------------------------------------------------------ listar

    @Test
    void authorSeesTheirPendingQuestionMarkedAsMine() {
        when(queries.publicList(productId, personId, null, 10, 0)).thenReturn(List.of(
                row(UUID.randomUUID(), otherPerson, "Sí, sirve."), row(questionId, personId, null)));
        when(queries.publicCount(productId, personId, null)).thenReturn(2L);

        PageResponse<ProductQuestionResponse> page = service.list(productId, null, null, null, EMAIL);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).mine()).isFalse();
        assertThat(page.items().get(0).answer().text()).isEqualTo("Sí, sirve.");
        assertThat(page.items().get(1).mine()).isTrue();
        assertThat(page.items().get(1).answer()).isNull();
        assertThat(page.total()).isEqualTo(2);
    }

    @Test
    void anonymousVisitorsAskOnlyForAnsweredQuestions() {
        when(queries.publicList(eq(productId), isNull(), any(), anyInt(), anyInt())).thenReturn(List.of());

        service.list(productId, 2, 5, null, null);

        verify(queries).publicList(productId, null, null, 5, 5);
        String sql = QuestionQueries.publicListSql(false, false);
        assertThat(sql).contains("q.answer IS NOT NULL", "q.deleted_at IS NULL", "q.hidden_at IS NULL")
                .doesNotContain(":viewer").doesNotContain("email");
        assertThat(QuestionQueries.publicListSql(true, false))
                .contains("(q.answer IS NOT NULL OR q.person_id = :viewer)", "q.hidden_at IS NULL")
                .contains("ORDER BY q.answered_at DESC NULLS LAST, q.created_at DESC");
    }

    @Test
    void searchIsEscapedAndLimitedToEightyCharacters() {
        service.list(productId, null, null, " 50%_off\\ ", null);

        verify(queries).publicList(productId, null, "%50\\%\\_off\\\\%", 10, 0);
        assertThat(QuestionQueries.publicListSql(false, true)).contains("q.question ILIKE :pattern");
        assertThatThrownBy(() -> service.list(productId, null, null, "x".repeat(81), null))
                .isInstanceOfSatisfying(BadRequestException.class,
                        error -> assertThat(error.getCode()).isEqualTo("QUESTION_QUERY_INVALID"));
    }

    @Test
    void controllerTreatsAnonymousAuthenticationAsNoSession() {
        assertThat(ProductQuestionController.emailOrNull(null)).isNull();
        assertThat(ProductQuestionController.emailOrNull(new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))))).isNull();
        assertThat(ProductQuestionController.emailOrNull(UsernamePasswordAuthenticationToken.authenticated(EMAIL,
                null, List.of()))).isEqualTo(EMAIL);
    }

    // ------------------------------------------------------------ borrar

    @Test
    void authorDeletesTheirUnansweredQuestion() {
        when(queries.find(questionId)).thenReturn(Optional.of(row(questionId, personId, null)));
        when(queries.softDeleteUnanswered(questionId, personId)).thenReturn(true);

        service.delete(productId, questionId, EMAIL);

        verify(queries).softDeleteUnanswered(questionId, personId);
    }

    @Test
    void answeredQuestionsCannotBeDeleted() {
        when(queries.find(questionId)).thenReturn(Optional.of(row(questionId, personId, "Sí")));

        assertThatThrownBy(() -> service.delete(productId, questionId, EMAIL))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("QUESTION_ALREADY_ANSWERED"));
        verify(queries, never()).softDeleteUnanswered(any(), any());
    }

    @Test
    void someoneElsesQuestionOrAnotherProductIsNotFound() {
        when(queries.find(questionId)).thenReturn(Optional.of(row(questionId, otherPerson, null)));

        assertThatThrownBy(() -> service.delete(productId, questionId, EMAIL))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.getCode()).isEqualTo("QUESTION_NOT_FOUND"));
        assertThatThrownBy(() -> service.delete(UUID.randomUUID(), questionId, EMAIL))
                .isInstanceOf(NotFoundException.class);
        verify(queries, never()).softDeleteUnanswered(any(), any());
    }
}
