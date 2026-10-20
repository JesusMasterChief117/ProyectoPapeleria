package com.paperstation.pos.service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Instantánea inmutable y exacta ({@link BigDecimal}) de los totales del carrito.
 * Útil para tickets, persistencia o auditoría, donde no debe usarse {@code double}.
 */
public record CartTotals(BigDecimal subtotal, BigDecimal discount, BigDecimal tax, BigDecimal total) {

    public static final CartTotals ZERO = new CartTotals(
            BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));

    public CartTotals {
        Objects.requireNonNull(subtotal, "subtotal");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(tax, "tax");
        Objects.requireNonNull(total, "total");
    }

    /** Base gravable: subtotal menos descuento. */
    public BigDecimal taxableBase() {
        return subtotal.subtract(discount);
    }
}
