package com.autopartco.backend.core.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Todos los correos comparten la identidad de Autopartco y escapan el texto variable. */
class EmailTemplateStyleTest {

    @Test
    void layoutUsesTheBrandAndNotTheOldBlue() {
        String html = EmailTemplate.layout("Titulo <x>", EmailTemplate.paragraph("Hola"));

        assertThat(html)
                .contains(EmailTemplate.GRAPHITE).contains(EmailTemplate.RED).contains(EmailTemplate.PAPER)
                .contains("Autopart<span style=\"color:" + EmailTemplate.RED_ON_DARK + ";\">co</span>")
                .contains("Titulo &lt;x&gt;")
                .doesNotContain("#0B3D91").doesNotContain("<script");
    }

    @Test
    void buttonIsTheRedPrimaryActionAndShowsTheLinkAsText() {
        String html = EmailTemplate.button("Aceptar", "https://autopartco.co/a?b=1&c=<2>");

        assertThat(html).contains("background:" + EmailTemplate.RED)
                .contains("href=\"https://autopartco.co/a?b=1&amp;c=&lt;2&gt;\"")
                .contains("Si el botón no funciona, copia este enlace");
    }

    @Test
    void listsDetailsAndCodesEscapeUserText() {
        assertThat(EmailTemplate.bulletList(List.of("Stock <bajo>"))).contains("Stock &lt;bajo&gt;");
        assertThat(EmailTemplate.detail("Rol", "<b>Admin</b>", false)).contains("&lt;b&gt;Admin&lt;/b&gt;");
        assertThat(EmailTemplate.highlightedCode("123456")).contains("letter-spacing:8px");
        assertThat(EmailTemplate.highlightedCode("a-very-long-recovery-token-value")).contains("word-break:break-all");
    }
}
