package com.autopartco.backend.core.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReviewInvitationEmailTest {
    @Test
    void templateHasTheCallToActionAndEscapesTheName() {
        String html = EmailTemplate.reviewInvitation("<b>Ana</b>", 1042,
                "https://autopartco.co/cuenta/calificar?pedido=abc&x=1");

        assertThat(html).contains("C&oacute;mo te fue con tu pedido?").contains("#1042").contains("Calificar mi pedido")
                .contains("pedido=abc&amp;x=1").doesNotContain("<b>Ana</b>").contains("&lt;b&gt;Ana");
    }
}
