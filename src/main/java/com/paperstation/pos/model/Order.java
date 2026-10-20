package com.paperstation.pos.model;

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
 */
public record Order(
        String orderId,
        String cashierName,
        LocalDateTime date,
        List<OrderItem> items
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
    }

    /** Suma de importes de línea antes de descuentos e impuestos. */
    public BigDecimal subtotal() {
        return items.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(Product.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Total de unidades vendidas. */
    public int totalUnits() {
        return items.stream().mapToInt(OrderItem::quantity).sum();
    }
}
