package com.autopartco.backend.marketplace.checkout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.order.OrderPlacement;
import com.autopartco.backend.core.order.PaymentMethodOption;
import com.autopartco.backend.core.payments.PaymentSettings;
import com.autopartco.backend.core.payments.WompiEnvironment;
import com.autopartco.backend.core.payments.WompiProperties;

class CheckoutOptionsTest {

    private static PaymentSettings settings(String provider, WompiEnvironment environment) {
        return new PaymentSettings(provider, 30, new WompiProperties(environment, null, null, null, null, null, null));
    }

    @Test
    void simulatedProviderListsActiveSimulatedMethodsWithoutEnvironment() {
        OrderPlacement placement = mock(OrderPlacement.class);
        when(placement.activePaymentMethods("SIMULATED_")).thenReturn(List.of(
                new PaymentMethodOption("SIMULATED_CARD", "Tarjeta simulada")));

        CheckoutOptionsResponse options = new CheckoutOptions(settings("", WompiEnvironment.SANDBOX), placement)
                .current();

        assertThat(options.provider()).isEqualTo("SIMULATED");
        assertThat(options.environment()).isNull();
        assertThat(options.methods()).containsExactly(
                new CheckoutOptionsResponse.Method("SIMULATED_CARD", "Tarjeta simulada"));
    }

    @Test
    void wompiProviderListsWompiMethodAndEnvironment() {
        OrderPlacement placement = mock(OrderPlacement.class);
        when(placement.isActivePaymentMethod("WOMPI")).thenReturn(true);

        CheckoutOptionsResponse options =
                new CheckoutOptions(settings("WOMPI", WompiEnvironment.PRODUCTION), placement).current();

        assertThat(options.provider()).isEqualTo("WOMPI");
        assertThat(options.environment()).isEqualTo("PRODUCTION");
        assertThat(options.methods()).containsExactly(new CheckoutOptionsResponse.Method(
                "WOMPI", "Wompi: tarjeta, PSE, Nequi, Bancolombia"));
    }

    @Test
    void inactiveWompiMethodIsNotOffered() {
        OrderPlacement placement = mock(OrderPlacement.class);
        when(placement.isActivePaymentMethod("WOMPI")).thenReturn(false);

        assertThat(new CheckoutOptions(settings("WOMPI", WompiEnvironment.SANDBOX), placement).current().methods())
                .isEmpty();
    }
}
