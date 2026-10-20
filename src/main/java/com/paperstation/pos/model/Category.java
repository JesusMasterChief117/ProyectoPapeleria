package com.paperstation.pos.model;

/**
 * Categorías principales de productos de papelería.
 */
public enum Category {
    TODOS("Todos"),
    DESTACADOS("Destacados"),
    PAPELERIA("Papelería"),
    CIBERCAFE("Cibercafé"),
    ESCOLAR("Escolar y Papelería"),
    ESCRITURA("Bolígrafos y Escritura"),
    ARTE("Arte y Diseño"),
    OFICINA("Oficina y Archivo"),
    COPIAS_E_IMPRESIONES("Copias e Impresiones");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
