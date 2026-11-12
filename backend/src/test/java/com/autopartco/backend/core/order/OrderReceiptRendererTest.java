package com.autopartco.backend.core.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Comprobante imprimible: datos del pedido, aviso de que no es factura y texto de usuario escapado. */
class OrderReceiptRendererTest {

    @Test
    void rendersTheOrderAndEscapesUserText() {
        BuyerOrderResponse.Item item = new BuyerOrderResponse.Item(UUID.randomUUID(), "Filtro <script>alert(1)</script>",
                "OEM-1&2", 2, new BigDecimal("45000.00"), new BigDecimal("90000.00"));
        BuyerOrderResponse.Sale sale = new BuyerOrderResponse.Sale(UUID.randomUUID(), UUID.randomUUID(),
                "Repuestos \"El Cruce\"", UUID.randomUUID(), "Sede Norte", "DELIVERED", new BigDecimal("90000.00"),
                new BigDecimal("8000.00"), new BigDecimal("98000.00"), List.of(item), null, null);
        BuyerOrderResponse order = new BuyerOrderResponse(UUID.randomUUID(), 1042L, "CONFIRMED",
                LocalDateTime.of(2026, 11, 10, 15, 30), "COP", new BigDecimal("90000.00"), new BigDecimal("8000.00"),
                new BigDecimal("98000.00"),
                new BuyerOrderResponse.Payment("APPROVED", "CARD", "Tarjeta", "ref-<1>", new BigDecimal("98000.00"),
                        LocalDateTime.of(2026, 11, 10, 15, 31)),
                new BuyerOrderResponse.DeliveryAddress("Calle 10 # 20-30", null, "Bogotá", "Cundinamarca", "Colombia", null),
                List.of(sale));

        String html = OrderReceiptRenderer.render(order);

        assertThat(html).startsWith("<!doctype html>").contains("Pedido #1042").contains("no es una factura")
                .contains("Calle 10 # 20-30").contains("Repuestos &quot;El Cruce&quot;").contains("OEM-1&amp;2")
                .contains("ref-&lt;1&gt;").contains("Filtro &lt;script&gt;")
                .doesNotContain("<script>alert").doesNotContain("http://").doesNotContain("https://");
        assertThat(OrderReceiptRenderer.money(new BigDecimal("98000.00"))).contains("98.000");
    }
}
