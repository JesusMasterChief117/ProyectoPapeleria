package com.paperstation.pos.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Instantánea inmutable de una línea vendida dentro de un {@link Order}.
 *
 * <p>A diferencia de {@link CartItem} (que contiene una propiedad observable y mutable),
 * esta línea congela la cantidad y el precio en el momento del cobro, de modo que el
 * ticket histórico no cambie aunque después se modifique el catálogo o el carrito.</p>
 *
 * @param product   producto vendido
 * @param quantity  cantidad vendida, {@code >= 1}
 * @param unitPrice precio unitario aplicado en la venta
 */
public record OrderItem(Product product, int quantity, BigDecimal unitPrice) {

    public OrderItem {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (quantity < 1) {
            throw new IllegalArgumentException("La cantidad debe ser al menos 1: " + quantity);
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo: " + unitPrice);
        }
        unitPrice = unitPrice.setScale(Product.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Congela el estado actual de una línea del carrito. */
    public static OrderItem from(CartItem item) {
        return new OrderItem(item.product(), item.getQuantity(), item.unitPrice());
    }

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(Product.MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
