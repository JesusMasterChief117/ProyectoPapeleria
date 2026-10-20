package com.paperstation.pos.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Producto del catálogo de la papelería (modelo inmutable).
 *
 * <p>El precio se normaliza a 2 decimales ({@link RoundingMode#HALF_UP}) para que
 * dos productos con el mismo precio lógico ({@code 28.5} vs {@code 28.50}) sean iguales
 * y para que todos los cálculos financieros partan de la misma escala.</p>
 *
 * @param id          identificador único / SKU (también usado como código de escaneo)
 * @param name        nombre comercial
 * @param description descripción corta (nunca {@code null})
 * @param price       precio unitario antes de impuestos, {@code >= 0}
 * @param category    categoría del catálogo
 * @param iconName    nombre lógico del icono (p. ej. código Ikonli); nunca {@code null}
 */
public record Product(
        String id,
        String name,
        String description,
        BigDecimal price,
        Category category,
        String iconName
) {

    public static final int MONEY_SCALE = 2;

    public Product {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(category, "category");

        if (id.isBlank()) {
            throw new IllegalArgumentException("El id del producto no puede estar vacío");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto no puede estar vacío");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo: " + price);
        }
        if (category == Category.TODOS) {
            throw new IllegalArgumentException("TODOS es un filtro, no una categoría asignable");
        }

        id = id.strip();
        name = name.strip();
        description = description == null ? "" : description.strip();
        price = price.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        iconName = iconName == null ? "" : iconName.strip();
    }

    @Override
    public String toString() {
        return name + " [" + id + "]";
    }
}
