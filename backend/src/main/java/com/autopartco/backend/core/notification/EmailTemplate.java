package com.autopartco.backend.core.notification;

import org.springframework.web.util.HtmlUtils;

/** Plantilla HTML común de los correos de Autopartco (cabecera de marca, cuerpo, pie). Todo texto variable se escapa. */
final class EmailTemplate {
    private static final String BRAND_COLOR = "#0B3D91";

    private EmailTemplate() {
    }

    static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    /** Párrafo; {@code html} debe venir ya escapado. */
    static String paragraph(String html) {
        return "<p style=\"margin:0 0 16px;font-size:15px;line-height:22px;color:#333333;\">" + html + "</p>";
    }

    /** Botón con enlace; el texto y la URL se escapan aquí. */
    static String button(String label, String url) {
        return "<p style=\"margin:24px 0;text-align:center;\"><a href=\"" + escape(url)
                + "\" style=\"display:inline-block;padding:12px 28px;background:" + BRAND_COLOR
                + ";color:#ffffff;text-decoration:none;border-radius:6px;font-weight:bold;font-size:15px;\">"
                + escape(label) + "</a></p>";
    }

    /** Código destacado (p. ej. 6 dígitos); se escapa aquí. */
    static String highlightedCode(String code) {
        return "<p style=\"margin:16px 0;text-align:center;font-size:32px;letter-spacing:8px;font-weight:bold;"
                + "font-family:'Courier New',monospace;color:" + BRAND_COLOR + ";background:#F2F5FA;"
                + "padding:14px 8px;border-radius:6px;\">" + escape(code) + "</p>";
    }

    static String note(String html) {
        return "<p style=\"margin:16px 0 0;font-size:13px;line-height:19px;color:#6b6b6b;\">" + html + "</p>";
    }

    /** Correo «¿Cómo te fue con tu pedido?»: invitación a calificar un pedido entregado. */
    static String reviewInvitation(String givenName, long orderNumber, String reviewLink) {
        return layout("¿Cómo te fue con tu pedido?",
                paragraph("Hola " + escape(givenName) + ",")
                        + paragraph("Tu pedido #" + orderNumber + " fue entregado. Cuéntanos qué tal estuvo el "
                                + "almacén y los repuestos que recibiste.")
                        + button("Calificar mi pedido", reviewLink)
                        + paragraph("Tu opinión ayuda a otros compradores a elegir mejor y a los almacenes a mejorar.")
                        + note("Calificar toma un minuto y es opcional. Si no quieres hacerlo, puedes ignorar este correo."));
    }

    static String reviewReminder(String givenName, long orderNumber, String reviewLink, String settingsLink) {
        return layout("¿Ya probaste lo que compraste?",
                paragraph("Hola " + escape(givenName) + ",")
                        + paragraph("Hace unos días recibiste tu pedido #" + orderNumber + ". Si ya usaste los repuestos, "
                                + "cuéntanos cómo te fue con ellos y con el almacén.")
                        + button("Calificar mi compra", reviewLink)
                        + note("Es opcional y no te lo recordaremos más de dos veces. Puedes desactivar estos recordatorios "
                                + "en tus preferencias: " + escape(settingsLink)));
    }

    /** Cita (pregunta o respuesta); se escapa aquí. */
    static String quote(String text) {
        return "<p style=\"margin:0 0 16px;padding:12px 16px;border-left:4px solid " + BRAND_COLOR
                + ";background:#F2F5FA;font-size:15px;line-height:22px;color:#222222;\">" + escape(text) + "</p>";
    }

    /** Correo al almacén: pregunta nueva sobre uno de sus productos, con enlace al portal. */
    static String questionAsked(String storeName, String productName, String question, String portalLink) {
        return layout("Nueva pregunta sobre " + productName,
                paragraph("Un comprador preguntó por <strong>" + escape(productName) + "</strong> en "
                        + escape(storeName) + ":")
                        + quote(question)
                        + button("Responder en el portal", portalLink)
                        + paragraph("Responder rápido ayuda a cerrar la venta y queda visible para otros compradores.")
                        + note("Recibes este correo porque tu almacén tiene activos los avisos de preguntas. Puedes "
                                + "cambiarlo en Configuración &gt; Notificaciones."));
    }

    /** Correo al comprador: el almacén respondió su pregunta. */
    static String questionAnswered(String givenName, String storeName, String productName, String question,
            String answer, String productLink) {
        return layout("Respondieron tu pregunta",
                paragraph("Hola " + escape(givenName) + ",")
                        + paragraph(escape(storeName) + " respondió tu pregunta sobre <strong>"
                                + escape(productName) + "</strong>.")
                        + quote(question)
                        + quote(answer)
                        + button("Ver la respuesta", productLink)
                        + note("Recibes este correo porque tienes activos los avisos de respuestas de almacenes. "
                                + "Puedes cambiarlo en tu cuenta, en Configuración."));
    }

    /** Envuelve el cuerpo ya construido con cabecera y pie de marca. */
    static String layout(String title, String bodyHtml) {
        return "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + escape(title)
                + "</title></head><body style=\"margin:0;padding:0;background:#EEF1F6;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#EEF1F6;\">"
                + "<tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:560px;width:100%;background:#ffffff;border-radius:8px;overflow:hidden;\">"
                + "<tr><td style=\"background:" + BRAND_COLOR + ";padding:20px 28px;color:#ffffff;font-size:22px;"
                + "font-weight:bold;letter-spacing:0.5px;\">Autopartco</td></tr>"
                + "<tr><td style=\"padding:28px;\"><h1 style=\"margin:0 0 16px;font-size:20px;color:#111111;\">"
                + escape(title) + "</h1>" + bodyHtml + "</td></tr>"
                + "<tr><td style=\"padding:16px 28px;background:#F7F8FA;font-size:12px;color:#8a8a8a;text-align:center;\">"
                + "Autopartco · Repuestos automotrices en Colombia</td></tr>"
                + "</table></td></tr></table></body></html>";
    }
}
