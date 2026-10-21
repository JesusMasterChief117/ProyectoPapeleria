package com.paperstation.pos.service;

import com.paperstation.pos.model.CartItem;
import com.paperstation.pos.model.Order;
import com.paperstation.pos.model.OrderItem;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Servicio de formateo de tickets y pre-cuentas para PaperStation POS.
 *
 * <p>Desacopla la generación textual de recibos respecto de los componentes visuales
 * ({@code CheckoutPanel}) y utiliza instancias de {@link NumberFormat} aisladas por hilo
 * mediante {@link ThreadLocal} para garantizar seguridad en concurrencia.</p>
 */
public final class ReceiptFormatter {

    private static final Locale LOCALE_MX = Locale.of("es", "MX");
    private static final DateTimeFormatter RECEIPT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", LOCALE_MX);

    private static final ThreadLocal<NumberFormat> CURRENCY_FORMAT =
            ThreadLocal.withInitial(() -> NumberFormat.getCurrencyInstance(LOCALE_MX));

    /**
     * Formatea un importe monetario de forma thread-safe en pesos mexicanos (MXN).
     */
    public static String formatCurrency(BigDecimal amount) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        return CURRENCY_FORMAT.get().format(safeAmount);
    }

    /**
     * Genera el texto de vista previa del ticket para el carrito activo.
     */
    public String formatPreTicket(
            String registerId,
            String orderNumberText,
            LocalDateTime timestamp,
            List<CartItem> items,
            CartTotals totals
    ) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(totals, "totals");

        StringBuilder receipt = new StringBuilder(320);
        receipt.append("PaperStation POS · ").append(registerId != null ? registerId : "Caja 01").append("\n");
        receipt.append(orderNumberText != null ? orderNumberText : "Orden")
                .append("   ")
                .append((timestamp != null ? timestamp : LocalDateTime.now()).format(RECEIPT_DATE_FORMAT))
                .append("\n");
        receipt.append("----------------------------------------\n");

        for (CartItem item : items) {
            String name = item.getProductName();
            String truncatedName = name.length() > 22 ? name.substring(0, 22) : name;
            receipt.append(String.format("%dx %-22s %10s%n",
                    item.getQuantity(),
                    truncatedName,
                    formatCurrency(item.getTotal())));
        }

        appendTotalsFooter(receipt, totals);
        return receipt.toString();
    }

    /**
     * Genera el texto del recibo definitivo de una orden cobrada.
     */
    public String formatOrderReceipt(String registerId, Order order) {
        Objects.requireNonNull(order, "order");

        StringBuilder receipt = new StringBuilder(360);
        receipt.append("PaperStation POS · ").append(registerId != null ? registerId : "Caja 01").append("\n");
        receipt.append("Folio: ").append(order.orderId())
                .append(" · Cajero: ").append(order.cashierName()).append("\n");
        receipt.append("Fecha: ").append(order.date().format(RECEIPT_DATE_FORMAT)).append("\n");
        receipt.append("----------------------------------------\n");

        for (OrderItem item : order.items()) {
            String name = item.product().name();
            String truncatedName = name.length() > 22 ? name.substring(0, 22) : name;
            receipt.append(String.format("%dx %-22s %10s%n",
                    item.quantity(),
                    truncatedName,
                    formatCurrency(item.lineTotal())));
        }

        appendTotalsFooter(receipt, order.totals());
        return receipt.toString();
    }

    private void appendTotalsFooter(StringBuilder receipt, CartTotals totals) {
        BigDecimal disc = totals.discount();
        String formattedDiscount = disc.signum() > 0
                ? "−" + formatCurrency(disc)
                : formatCurrency(BigDecimal.ZERO);

        receipt.append("----------------------------------------\n");
        receipt.append("Subtotal: ").append(formatCurrency(totals.subtotal())).append("\n");
        receipt.append("Descuento: ").append(formattedDiscount).append("\n");
        receipt.append("IVA (16%): ").append(formatCurrency(totals.tax())).append("\n");
        receipt.append("TOTAL: ").append(formatCurrency(totals.total()));
    }
}
