package com.autopartco.backend.marketplace.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.order.BuyerOrderQueries;
import com.autopartco.backend.core.order.BuyerOrderResponse;
import com.autopartco.backend.core.order.BuyerOrderSummary;
import com.autopartco.backend.shared.web.PageResponse;

class MyOrdersControllerTest {
    private static final String EMAIL = "comprador@example.com";
    private static final UUID PERSON_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    private final BuyerOrderQueries queries = mock(BuyerOrderQueries.class);
    private final AccountLookup accountLookup = mock(AccountLookup.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    private final MyOrdersController controller = new MyOrdersController(queries, accountLookup);
    private final Authentication authentication = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());

    @Test
    void listUsesThePersonOfTheAuthenticatedAccount() {
        when(accountLookup.requireByEmail(EMAIL).personId()).thenReturn(PERSON_ID);
        PageResponse<BuyerOrderSummary> expected = PageResponse.of(List.of(), 3, 20, 0);
        when(queries.list(PERSON_ID, 3, 20)).thenReturn(expected);

        assertThat(controller.list(3, 20, authentication)).isSameAs(expected);
    }

    @Test
    void detailUsesThePersonOfTheAuthenticatedAccount() {
        when(accountLookup.requireByEmail(EMAIL).personId()).thenReturn(PERSON_ID);
        BuyerOrderResponse expected = mock(BuyerOrderResponse.class);
        when(queries.detail(PERSON_ID, ORDER_ID)).thenReturn(expected);

        assertThat(controller.detail(ORDER_ID, authentication)).isSameAs(expected);
        verify(queries).detail(PERSON_ID, ORDER_ID);
    }

    @Test
    void pageResponseComputesTotalPagesRoundingUp() {
        assertThat(PageResponse.of(List.of(), 1, 10, 11).totalPages()).isEqualTo(2);
        assertThat(PageResponse.of(List.of(), 1, 10, 10).totalPages()).isEqualTo(1);
        assertThat(PageResponse.of(List.of(), 9, 10, 0).totalPages()).isZero();
    }
}
