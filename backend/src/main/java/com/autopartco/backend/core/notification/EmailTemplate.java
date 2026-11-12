package com.autopartco.backend.core.notification;

import org.springframework.web.util.HtmlUtils;

/**
 * Plantilla HTML común de los correos de Autopartco, con la identidad del sitio (docs/design/AUTOPARTCO_DESIGN_SYSTEM.md):
 * cabecera grafito con el imagotipo en texto («Autopart» + «co» en rojo) y línea roja, fondo papel, rojo solo para la
 * acción principal y datos (códigos, referencias) en monoespaciada. Solo estilos en línea y tablas, para que se vea
 * igual en Gmail, Outlook y móviles. Todo texto variable se escapa.
 */
final class EmailTemplate {
    static final String GRAPHITE = "#202326";
    static final String INK = "#292D31";
    static final String MUTED = "#626A70";
    static final String RED = "#C9272C";
    static final String RED_ON_DARK = "#F55058";
    static final String PAPER = "#F4F3EF";
    static final String LINE = "#DDE0E2";
    private static final String SANS = "'IBM Plex Sans',Inter,'Segoe UI',Roboto,Arial,Helvetica,sans-serif";
    private static final String MONO = "'IBM Plex Mono',ui-monospace,Menlo,Consolas,'Courier New',monospace";

    private EmailTemplate() {
    }

    static String escape(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    /** Párrafo; {@code html} debe venir ya escapado. */
    static String paragraph(String html) {
        return "<p style=\"margin:0 0 16px;font-size:15px;line-height:23px;color:" + INK + ";\">" + html + "</p>";
    }

    /** Botón con enlace (acción principal, en rojo) y el enlace en texto debajo; se escapan aquí. */
    static String button(String label, String url) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:24px 0 8px;\"><tr>"
                + "<td style=\"background:" + RED + ";border-radius:6px;\"><a href=\"" + escape(url)
                + "\" style=\"display:inline-block;padding:13px 26px;color:#FFFFFF;text-decoration:none;font-weight:600;"
                + "font-size:15px;line-height:18px;font-family:" + SANS + ";\">" + escape(label) + "</a></td></tr></table>"
                + "<p style=\"margin:0 0 20px;font-size:12px;line-height:18px;color:" + MUTED + ";word-break:break-all;\">"
                + "Si el botón no funciona, copia este enlace: " + escape(url) + "</p>";
    }

    /** Código destacado (6 dígitos o un token largo); se escapa aquí. */
    static String highlightedCode(String code) {
        boolean shortCode = code != null && code.length() <= 8;
        return "<p style=\"margin:16px 0;padding:14px 12px;text-align:center;background:" + PAPER + ";border:1px solid "
                + LINE + ";border-radius:6px;font-family:" + MONO + ";font-weight:600;color:" + GRAPHITE + ";"
                + (shortCode ? "font-size:30px;line-height:36px;letter-spacing:8px;"
                        : "font-size:15px;line-height:22px;letter-spacing:1px;word-break:break-all;")
                + "\">" + escape(code) + "</p>";
    }

    /** Lista con líneas de estructura; cada elemento se escapa aquí. */
    static String bulletList(java.util.List<String> items) {
        StringBuilder html = new StringBuilder("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
                + "width=\"100%\" style=\"margin:0 0 16px;border-top:1px solid " + LINE + ";\">");
        for (String item : items) {
            html.append("<tr><td style=\"padding:10px 0;border-bottom:1px solid ").append(LINE)
                    .append(";font-size:15px;line-height:22px;color:").append(INK).append(";\">")
                    .append(escape(item)).append("</td></tr>");
        }
        return html.append("</table>").toString();
    }

    /** Etiqueta y valor (fecha, dispositivo…); se escapan aquí. {@code data}: valor en monoespaciada. */
    static String detail(String label, String value, boolean data) {
        return "<p style=\"margin:0 0 6px;font-size:14px;line-height:21px;color:" + INK + ";\"><span style=\"color:"
                + MUTED + ";\">" + escape(label) + ":</span> <span style=\"" + (data ? "font-family:" + MONO + ";" : "")
                + "\">" + escape(value) + "</span></p>";
    }

    static String note(String html) {
        return "<p style=\"margin:16px 0 0;font-size:13px;line-height:19px;color:" + MUTED + ";\">" + html + "</p>";
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
        return "<p style=\"margin:0 0 16px;padding:12px 16px;border-left:3px solid " + GRAPHITE + ";background:" + PAPER
                + ";font-size:15px;line-height:22px;color:" + INK + ";\">" + escape(text) + "</p>";
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
        return "<!DOCTYPE html><html lang=\"es-CO\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<meta name=\"color-scheme\" content=\"light\"><meta name=\"supported-color-schemes\" content=\"light\">"
                + "<title>" + escape(title) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background:" + PAPER + ";font-family:" + SANS + ";color:" + INK + ";\">"
                // Texto de vista previa en la bandeja de entrada; no se ve en el cuerpo.
                + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">" + escape(title) + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:" + PAPER + ";\">"
                + "<tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"max-width:560px;width:100%;background:#FFFFFF;border:1px solid " + LINE + ";border-radius:8px;overflow:hidden;\">"
                + "<tr><td style=\"background:" + GRAPHITE + ";border-bottom:3px solid " + RED + ";padding:18px 28px;\">"
                + "<span style=\"font-family:" + SANS + ";font-size:22px;line-height:26px;font-weight:800;letter-spacing:-0.5px;"
                + "color:#FFFFFF;\">Autopart<span style=\"color:" + RED_ON_DARK + ";\">co</span></span></td></tr>"
                + "<tr><td style=\"padding:28px 28px 24px;\">"
                + "<h1 style=\"margin:0 0 18px;font-family:" + SANS + ";font-size:22px;line-height:28px;font-weight:700;color:"
                + GRAPHITE + ";\">" + escape(title) + "</h1>" + bodyHtml + "</td></tr>"
                + "<tr><td style=\"padding:16px 28px;border-top:1px solid " + LINE + ";\">"
                + "<p style=\"margin:0;font-family:" + MONO + ";font-size:11px;line-height:16px;letter-spacing:1px;"
                + "text-transform:uppercase;color:" + MUTED + ";\">Autopartco · Repuestos de los almacenes de tu ciudad</p>"
                + "<p style=\"margin:6px 0 0;font-size:12px;line-height:18px;color:" + MUTED + ";\">"
                + "<a href=\"https://www.autopartco.co\" style=\"color:" + MUTED + ";text-decoration:underline;\">autopartco.co</a>"
                + " · Correo automático: no respondas a esta dirección.</p></td></tr>"
                + "</table></td></tr></table></body></html>";
    }
}
