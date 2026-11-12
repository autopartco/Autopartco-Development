package com.autopartco.backend.core.order;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Comprobante de compra imprimible (HTML autónomo, sin scripts ni recursos externos). No es una factura: la emite cada
 * almacén. Todo texto variable se escapa.
 */
public final class OrderReceiptRenderer {
    private static final Locale CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, h:mm a", CO);

    private OrderReceiptRenderer() {
    }

    public static String render(BuyerOrderResponse order) {
        StringBuilder html = new StringBuilder(4096);
        html.append("""
                <!doctype html>
                <html lang="es-CO"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <meta name="robots" content="noindex">
                <title>Comprobante de compra #""").append(order.orderNumber()).append("""
                 | Autopartco</title>
                <style>
                  body{font:14px/1.45 system-ui,-apple-system,"Segoe UI",sans-serif;color:#202326;margin:0;background:#fff}
                  main{max-width:760px;margin:0 auto;padding:32px 20px}
                  header{display:flex;justify-content:space-between;gap:16px;border-bottom:3px solid #c9272c;padding-bottom:12px}
                  h1{font-size:20px;margin:0}
                  h2{font-size:15px;margin:24px 0 8px}
                  .muted{color:#5c636a}
                  .mono{font-family:ui-monospace,Menlo,Consolas,monospace}
                  table{width:100%;border-collapse:collapse}
                  th,td{text-align:left;padding:6px 4px;border-bottom:1px solid #e3e5e8;vertical-align:top}
                  th{font-size:12px;text-transform:uppercase;letter-spacing:.04em;color:#5c636a}
                  .num{text-align:right;white-space:nowrap;font-variant-numeric:tabular-nums}
                  .total td{font-weight:700;border-bottom:0}
                  .note{margin-top:28px;font-size:12px}
                  .print{margin-top:20px}
                  @media print{.print{display:none}main{padding:0}}
                </style></head><body><main>
                <header><div><h1>Comprobante de compra</h1>
                <div class="muted">Autopartco · autopartco.co</div></div>
                <div class="num"><div class="mono">Pedido #""").append(order.orderNumber()).append("</div>");
        html.append("<div class=\"muted\">").append(esc(date(order.createdAt()))).append("</div></div></header>");

        if (order.deliveryAddress() != null) {
            BuyerOrderResponse.DeliveryAddress address = order.deliveryAddress();
            html.append("<h2>Entrega</h2><p>").append(esc(address.addressLine()));
            if (address.addressComplement() != null && !address.addressComplement().isBlank()) {
                html.append(", ").append(esc(address.addressComplement()));
            }
            html.append("<br>").append(esc(address.city())).append(", ").append(esc(address.department())).append("</p>");
        }

        for (BuyerOrderResponse.Sale sale : order.sales()) {
            html.append("<h2>").append(esc(sale.storeName()));
            if (sale.branchName() != null) {
                html.append(" <span class=\"muted\">· ").append(esc(sale.branchName())).append("</span>");
            }
            html.append("</h2><table><thead><tr><th>Producto</th><th class=\"num\">Cant.</th>")
                    .append("<th class=\"num\">Precio</th><th class=\"num\">Subtotal</th></tr></thead><tbody>");
            for (BuyerOrderResponse.Item item : sale.items()) {
                html.append("<tr><td>").append(esc(item.name()));
                if (item.partNumber() != null && !item.partNumber().isBlank()) {
                    html.append("<br><span class=\"muted mono\">Ref. ").append(esc(item.partNumber())).append("</span>");
                }
                html.append("</td><td class=\"num\">").append(item.quantity())
                        .append("</td><td class=\"num\">").append(money(item.unitPrice()))
                        .append("</td><td class=\"num\">").append(money(item.lineTotal())).append("</td></tr>");
            }
            html.append("<tr><td colspan=\"3\" class=\"muted\">Envío</td><td class=\"num\">")
                    .append(money(sale.shippingCost())).append("</td></tr>")
                    .append("<tr class=\"total\"><td colspan=\"3\">Total ").append(esc(sale.storeName()))
                    .append("</td><td class=\"num\">").append(money(sale.total())).append("</td></tr></tbody></table>");
        }

        html.append("<h2>Resumen</h2><table><tbody>")
                .append(row("Productos", money(order.subtotal())))
                .append(row("Envío", money(order.shippingTotal())))
                .append("<tr class=\"total\"><td>Total pagado</td><td class=\"num\">").append(money(order.total()))
                .append("</td></tr></tbody></table>");
        if (order.payment() != null) {
            BuyerOrderResponse.Payment payment = order.payment();
            html.append("<p class=\"muted\">Pago: ").append(esc(payment.methodName()));
            if (payment.reference() != null) {
                html.append(" · Ref. <span class=\"mono\">").append(esc(payment.reference())).append("</span>");
            }
            if (payment.paidAt() != null) {
                html.append(" · ").append(esc(date(payment.paidAt())));
            }
            html.append("</p>");
        }
        html.append("<p class=\"note muted\">Este comprobante resume tu compra en Autopartco y no es una factura. ")
                .append("La factura electrónica la emite cada almacén; si la adjuntó, la encuentras en el detalle del pedido.</p>")
                .append("<p class=\"print\"><button type=\"button\" onclick=\"window.print()\">Imprimir o guardar en PDF</button></p>")
                .append("</main></body></html>");
        return html.toString();
    }

    private static String row(String label, String value) {
        return "<tr><td>" + esc(label) + "</td><td class=\"num\">" + value + "</td></tr>";
    }

    static String money(BigDecimal value) {
        if (value == null) {
            return "—";
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(CO);
        format.setMaximumFractionDigits(0);
        format.setMinimumFractionDigits(0);
        return esc(format.format(value));
    }

    private static String date(LocalDateTime value) {
        return value == null ? "" : DATE.format(value);
    }

    static String esc(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
