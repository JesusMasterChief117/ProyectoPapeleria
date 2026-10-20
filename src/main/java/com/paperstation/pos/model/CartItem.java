package com.paperstation.pos.model;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.SimpleIntegerProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Línea del carrito de venta.
 *
 * <p>El record es inmutable en sus <em>referencias</em>: el producto no puede cambiarse y la
 * instancia de {@link SimpleIntegerProperty} es siempre la misma, lo que permite a la UI
 * enlazarse a ella de forma estable. Lo único que muta es el <em>valor</em> de la cantidad,
 * y esa mutación debe hacerse a través de {@code CartService} para garantizar las reglas
 * de negocio (cantidad mínima 1, recálculo de totales, etc.).</p>
 *
 * @param product  producto vendido (inmutable)
 * @param quantity cantidad observable, siempre {@code >= 1}
 */
public record CartItem(Product product, SimpleIntegerProperty quantity) {

    public CartItem {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.get() < 1) {
            throw new IllegalArgumentException("La cantidad debe ser al menos 1: " + quantity.get());
        }
    }

    /** Fábrica de conveniencia: crea la línea con su propia propiedad de cantidad. */
    public static CartItem of(Product product, int quantity) {
        return new CartItem(product, new SimpleIntegerProperty(quantity));
    }

    /** Valor actual de la cantidad. */
    public int getQuantity() {
        return quantity.get();
    }

    /** Precio unitario (atajo de {@code product().price()}). */
    public BigDecimal unitPrice() {
        return product.price();
    }

    /** Importe exacto de la línea: {@code precio × cantidad}, escala 2. */
    public BigDecimal lineTotal() {
        return product.price()
                .multiply(BigDecimal.valueOf(quantity.get()))
                .setScale(Product.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Producto vendido (alias JavaBean). */
    public Product getProduct() {
        return product;
    }

    /** Nombre del producto vendido. */
    public String getProductName() {
        return product.name();
    }

    /** Precio unitario (alias JavaBean). */
    public BigDecimal getUnitPrice() {
        return unitPrice();
    }

    /** Total de la línea (alias JavaBean). */
    public BigDecimal getTotal() {
        return lineTotal();
    }

    /**
     * Binding reactivo del importe de la línea, útil para celdas de tabla.
     * Se recalcula automáticamente cuando cambia la cantidad.
     */
    public ObjectBinding<BigDecimal> lineTotalBinding() {
        return Bindings.createObjectBinding(this::lineTotal, quantity);
    }

    // Nota: SimpleIntegerProperty no redefine equals/hashCode, por lo que el equals generado
    // del record compara la instancia de la propiedad (identidad de la línea), no su valor.

    @Override
    public String toString() {
        return quantity.get() + " × " + product.name();
    }
}
