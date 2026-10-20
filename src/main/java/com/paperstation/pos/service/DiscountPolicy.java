package com.paperstation.pos.service;

import com.paperstation.pos.model.CartItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Estrategia de descuento aplicable al carrito.
 *
 * <p>Recibe el subtotal exacto y una vista de solo lectura de las líneas, y devuelve el
 * <strong>monto</strong> a descontar. {@link CartService} se encarga de sanear el resultado:
 * valores {@code null} o negativos se tratan como cero y nunca se descuenta más que el subtotal.
 * Esto permite implementar promociones complejas (2x1, por categoría, por volumen...) sin
 * tocar el servicio.</p>
 */
@FunctionalInterface
public interface DiscountPolicy {

    BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /**
     * @param subtotal subtotal exacto del carrito (escala 2)
     * @param items    líneas actuales (solo lectura)
     * @return monto del descuento a aplicar
     */
    BigDecimal discountFor(BigDecimal subtotal, List<CartItem> items);

    /** Sin descuento. */
    static DiscountPolicy none() {
        return (subtotal, items) -> BigDecimal.ZERO;
    }

    /**
     * Descuento porcentual sobre el subtotal.
     *
     * @param percent porcentaje entre 0 y 100 (p. ej. {@code 10} = 10 %)
     */
    static DiscountPolicy percentage(BigDecimal percent) {
        Objects.requireNonNull(percent, "percent");
        if (percent.signum() < 0 || percent.compareTo(ONE_HUNDRED) > 0) {
            throw new IllegalArgumentException("El porcentaje debe estar entre 0 y 100: " + percent);
        }
        return (subtotal, items) -> subtotal.multiply(percent)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /** Descuento de monto fijo (se limita automáticamente al subtotal). */
    static DiscountPolicy fixedAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("El monto de descuento no puede ser negativo: " + amount);
        }
        return (subtotal, items) -> amount;
    }
}
