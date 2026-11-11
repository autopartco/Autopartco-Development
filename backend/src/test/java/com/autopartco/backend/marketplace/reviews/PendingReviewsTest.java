package com.autopartco.backend.marketplace.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.shared.storage.ImageUrls;
import com.autopartco.backend.shared.storage.ImageVariant;

class PendingReviewsTest {
    private static final String EMAIL = "ana@correo.co";

    private final PendingReviewQueries queries = mock(PendingReviewQueries.class);
    private final AccountLookup accountLookup = mock(AccountLookup.class);
    private final StoreAccess storeAccess = mock(StoreAccess.class);
    private final ImageUrls imageUrls = mock(ImageUrls.class);
    private final PendingReviews pending = new PendingReviews(queries, accountLookup, storeAccess, imageUrls);

    private final UUID personId = UUID.randomUUID();
    private final UUID order = UUID.randomUUID();
    private final UUID storeA = UUID.randomUUID();
    private final UUID storeB = UUID.randomUUID();
    private final UUID filter = UUID.randomUUID();
    private final UUID pads = UUID.randomUUID();
    private final OffsetDateTime delivered = OffsetDateTime.parse("2026-11-01T10:00:00Z");

    @BeforeEach
    void buyer() {
        when(accountLookup.requireByEmail(EMAIL)).thenReturn(new AccountRef(UUID.randomUUID(), personId, EMAIL,
                "Ana Pérez", "ACTIVE", "CUSTOMER", "Ana"));
        when(imageUrls.variant("p/filtro.webp", ImageVariant.THUMB)).thenReturn("https://cdn/filtro-thumb.webp");
    }

    @Test
    void groupsByOrderAndStoreAndMarksWhatIsAlreadyReviewed() {
        when(queries.orderStores(personId)).thenReturn(List.of(
                new PendingReviewQueries.OrderStore(order, 1042, storeA, "Repuestos A", delivered, true),
                new PendingReviewQueries.OrderStore(order, 1042, storeB, "Repuestos B", delivered, false)));
        when(queries.products(personId, Set.of(order))).thenReturn(List.of(
                new PendingReviewQueries.ProductLine(order, storeA, filter, "Filtro de aceite", "p/filtro.webp", false),
                new PendingReviewQueries.ProductLine(order, storeB, pads, "Pastillas", null, true)));

        List<PendingReview> result = pending.pending(EMAIL);

        assertThat(result).hasSize(2);
        PendingReview first = result.getFirst();
        assertThat(first.orderId()).isEqualTo(order);
        assertThat(first.orderNumber()).isEqualTo(1042);
        assertThat(first.deliveredAt()).isEqualTo(delivered);
        assertThat(first.store()).isEqualTo(new PendingReview.Store(storeA, "Repuestos A", true));
        assertThat(first.products()).containsExactly(
                new PendingReview.Product(filter, "Filtro de aceite", "https://cdn/filtro-thumb.webp", false));
        assertThat(result.get(1).store().reviewed()).isFalse();
        assertThat(result.get(1).products()).containsExactly(new PendingReview.Product(pads, "Pastillas", null, true));
    }

    @Test
    void entriesWithEverythingReviewedAreLeftOut() {
        when(queries.orderStores(personId)).thenReturn(List.of(
                new PendingReviewQueries.OrderStore(order, 7, storeA, "Repuestos A", delivered, true)));
        when(queries.products(eq(personId), any())).thenReturn(List.of(
                new PendingReviewQueries.ProductLine(order, storeA, filter, "Filtro", null, true)));

        assertThat(pending.pending(EMAIL)).isEmpty();
    }

    @Test
    void storesOfTheBuyersOwnTeamAreSkipped() {
        when(queries.orderStores(personId)).thenReturn(List.of(
                new PendingReviewQueries.OrderStore(order, 7, storeA, "Mi almacén", delivered, false)));
        when(storeAccess.isMember(personId, storeA)).thenReturn(true);

        assertThat(pending.pending(EMAIL)).isEmpty();
        verify(queries, never()).products(eq(personId), eq(Set.of(order)));
    }

    @Test
    void nothingDeliveredMeansNothingPending() {
        when(queries.orderStores(personId)).thenReturn(List.of());

        assertThat(pending.pending(EMAIL)).isEmpty();
        verify(queries, never()).products(any(), any());
    }

    @Test
    void onlyTheBuyersOwnOrdersWithinNinetyDaysAreRead() {
        assertThat(PendingReviewQueries.ORDER_STORES_SQL)
                .contains("o.person_id = :personId", "ss.code = 'DELIVERED'", "make_interval(days => :days)");
        assertThat(PendingReviewQueries.WINDOW_DAYS).isEqualTo(90);
        assertThat(PendingReviewQueries.PRODUCTS_SQL).contains("r.person_id = :personId", "r.deleted_at IS NULL");
    }
}
