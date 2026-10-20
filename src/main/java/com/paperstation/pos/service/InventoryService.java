package com.paperstation.pos.service;

import com.paperstation.pos.model.Category;
import com.paperstation.pos.model.Product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Servicio encargado de gestionar el inventario y búsqueda de artículos de papelería.
 *
 * <p>El {@link Product#id()} funciona como SKU/código de barras, por lo que también es la
 * clave usada por el escáner.</p>
 */
public class InventoryService {

    private final List<Product> catalog = List.of(
            new Product("7501001", "Cuaderno Profesional Raya Scribe 100h", "Cuaderno cosido, raya, 100 hojas", new BigDecimal("28.50"), Category.ESCOLAR, "notebook"),
            new Product("7501002", "Cuaderno Profesional Cuadro 7mm 100h", "Cuaderno cosido, cuadro chico, 100 hojas", new BigDecimal("28.50"), Category.ESCOLAR, "notebook"),
            new Product("7501003", "Bolígrafo BIC Cristal Mediano Azul", "Punto mediano 1.0 mm, tinta azul", new BigDecimal("8.00"), Category.ESCRITURA, "pen"),
            new Product("7501004", "Bolígrafo BIC Cristal Mediano Negro", "Punto mediano 1.0 mm, tinta negra", new BigDecimal("8.00"), Category.ESCRITURA, "pen"),
            new Product("7501005", "Plumón Permanente Sharpie Negro", "Punto fino, tinta permanente", new BigDecimal("24.00"), Category.ESCRITURA, "marker"),
            new Product("7501006", "Lápiz Adhesivo Pritt 42g Original", "Pegamento en barra lavable", new BigDecimal("35.00"), Category.ESCOLAR, "glue"),
            new Product("7501007", "Tijeras Escolares Maped Punta Roma", "Tijeras de seguridad 13 cm", new BigDecimal("22.50"), Category.ESCOLAR, "scissors"),
            new Product("7501008", "Caja Colores Prismacolor Junior 24pz", "Lápices de colores, 24 piezas", new BigDecimal("185.00"), Category.ARTE, "palette"),
            new Product("7501009", "Resma Papel Bond Carta 500 Hojas 75g", "Papel blanco multiusos tamaño carta", new BigDecimal("115.00"), Category.OFICINA, "paper"),
            new Product("7501010", "Folder Tamaño Carta Crema c/10", "Paquete con 10 folders", new BigDecimal("25.00"), Category.OFICINA, "folder"),
            new Product("7501011", "Copia Fotostática B/N Carta", "Copia blanco y negro por cara", new BigDecimal("1.00"), Category.COPIAS_E_IMPRESIONES, "copy"),
            new Product("7501012", "Impresión Color Láser Carta", "Impresión a color por cara", new BigDecimal("5.00"), Category.COPIAS_E_IMPRESIONES, "printer"),
            new Product("7501013", "Renta PC / Internet (1 Hora)", "Estación de cómputo con fibra óptica", new BigDecimal("20.00"), Category.CIBERCAFE, "computer"),
            new Product("7501014", "Escaneo Digital a PDF / USB", "Digitalización a color 300 DPI por hoja", new BigDecimal("6.00"), Category.CIBERCAFE, "scanner"),
            new Product("7501015", "Engargolado Plástico + Pastas", "Arillo plástico y pastas hasta 100 hojas", new BigDecimal("38.00"), Category.CIBERCAFE, "binding"),
            new Product("7501016", "Impresión Fotográfica 4x6\" Glossy", "Papel fotográfico brillante de 260g", new BigDecimal("12.00"), Category.CIBERCAFE, "photo")
    );

    private static final List<String> FEATURED_IDS = List.of(
            "7501001", "7501003", "7501005", "7501006",
            "7501008", "7501009", "7501011", "7501012",
            "7501013", "7501015"
    );

    public List<Product> getAllProducts() {
        return catalog;
    }

    public List<Product> search(String query, Category category) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return catalog.stream()
                .filter(p -> matchesCategory(p, category, !normalized.isEmpty()))
                .filter(p -> normalized.isEmpty() ||
                        p.name().toLowerCase(Locale.ROOT).contains(normalized) ||
                        p.description().toLowerCase(Locale.ROOT).contains(normalized) ||
                        p.id().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    private boolean matchesCategory(Product product, Category filter, boolean hasSearchQuery) {
        if (filter == null || filter == Category.TODOS) {
            return true;
        }
        if (filter == Category.DESTACADOS) {
            return hasSearchQuery || FEATURED_IDS.contains(product.id());
        }
        if (filter == Category.PAPELERIA) {
            return product.category() == Category.PAPELERIA
                    || product.category() == Category.ESCOLAR
                    || product.category() == Category.ESCRITURA
                    || product.category() == Category.ARTE
                    || product.category() == Category.OFICINA;
        }
        if (filter == Category.CIBERCAFE) {
            return product.category() == Category.CIBERCAFE
                    || product.category() == Category.COPIAS_E_IMPRESIONES;
        }
        return product.category() == filter;
    }

    /** Búsqueda exacta por id/SKU (entrada del escáner). */
    public Optional<Product> findById(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String key = id.trim();
        return catalog.stream()
                .filter(p -> p.id().equalsIgnoreCase(key))
                .findFirst();
    }

    /** Alias de conveniencia para findById */
    public Optional<Product> findByBarcode(String barcode) {
        return findById(barcode);
    }
}
