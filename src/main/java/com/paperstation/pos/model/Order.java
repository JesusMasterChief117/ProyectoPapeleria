package com.paperstation.pos.model;

import com.paperstation.pos.service.CartService;
import com.paperstation.pos.service.CartTotals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Venta confirmada (modelo inmutable).
 *
 * <p>Las líneas se copian defensivamente con {@link List#copyOf(java.util.Collection)}, por lo
 * que la lista resultante es inmutable y no comparte estado con el carrito de origen.</p>
 *
 * @param orderId     folio de la venta
 * @param cashierName nombre del cajero que realizó el cobro
 * @param date        fecha y hora de la venta
 * @param items       líneas vendidas (no vacía)
 * @param totals      instantánea exacta de los importes (subtotal, descuento, IVA y total) al cobrar
 */
public record Order(
        String orderId,
        String cashierName,
        LocalDateTime date,
        List<OrderItem> items,
        CartTotals totals
) {

    public Order {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(cashierName, "cashierName");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(items, "items");

        if (orderId.isBlank()) {
            throw new IllegalArgumentException("El folio no puede estar vacío");
        }
        if (cashierName.isBlank()) {
            throw new IllegalArgumentException("El nombre del cajero no puede estar vacío");
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Una orden debe contener al menos un artículo");
        }

        items = List.copyOf(items); // inmutable + rechaza elementos null
        if (totals == null) {
            BigDecimal sub = items.stream()
                    .map(OrderItem::lineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(Product.MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal disc = BigDecimal.ZERO.setScale(Product.MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal iva = sub.multiply(CartService.IVA_RATE).setScale(Product.MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal tot = sub.add(iva).setScale(Product.MONEY_SCALE, RoundingMode.HALF_UP);
            totals = new CartTotals(sub, disc, iva, tot);
        }
    }

    /** Constructor de compatibilidad sin desglose explícito de totales (asume descuento cero). */
    public Order(String orderId, String cashierName, LocalDateTime date, List<OrderItem> items) {
        this(orderId, cashierName, date, items, null);
    }

    /** Suma de importes de línea antes de descuentos e impuestos. */
    public BigDecimal subtotal() {
        return totals.subtotal();
    }

    /** Monto de descuento aplicado a la venta. */
    public BigDecimal discount() {
        return totals.discount();
    }

    /** Impuesto (IVA) calculado en la venta. */
    public BigDecimal tax() {
        return totals.tax();
    }

    /** Importe total final cobrado en la venta. */
    public BigDecimal total() {
        return totals.total();
    }

    /** Total de unidades vendidas. */
    public int totalUnits() {
        return items.stream().mapToInt(OrderItem::quantity).sum();
    }
}
