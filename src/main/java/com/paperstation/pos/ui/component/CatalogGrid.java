package com.paperstation.pos.ui.component;

import com.paperstation.pos.model.Product;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.List;
import java.util.function.Consumer;

/**
 * Grilla responsiva de productos ({@code CatalogGrid}) para la sección central/derecha del POS.
 *
 * <p>Extiende {@link ScrollPane} e integra un {@link TilePane} responsivo que recalcula el ancho
 * óptimo de columna según el viewport disponible, manteniendo márgenes de respiración para que
 * la sombra ({@code DropShadow}) de cada {@link ProductCardNode} nunca se recorte.</p>
 */
public class CatalogGrid extends ScrollPane {

    private static final double HGAP = 16.0;
    private static final double VGAP = 16.0;
    private static final double MIN_TILE_WIDTH = 196.0;
    private static final double TILE_HEIGHT = 174.0;
    private static final Insets GRID_PADDING = new Insets(8.0, 12.0, 22.0, 8.0);

    private final TilePane tilePane = new TilePane();
    private final VBox emptyStatePane;

    public CatalogGrid() {
        getStyleClass().add("catalog-grid-scroll");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        setPannable(false);

        tilePane.getStyleClass().add("catalog-tile-pane");
        tilePane.setHgap(HGAP);
        tilePane.setVgap(VGAP);
        tilePane.setPadding(GRID_PADDING);
        tilePane.setTileAlignment(Pos.TOP_LEFT);
        tilePane.setAlignment(Pos.TOP_LEFT);
        tilePane.setPrefTileWidth(212.0);
        tilePane.setPrefTileHeight(TILE_HEIGHT);

        emptyStatePane = buildEmptySearchState();

        setContent(tilePane);

        // Responsividad: ajusta el ancho de cada celda del TilePane según el ancho real del viewport
        viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            if (newBounds != null && newBounds.getWidth() > 0) {
                updateResponsiveColumns(newBounds.getWidth());
            }
        });
    }

    /**
     * Pobla la grilla con instancias de {@link ProductCardNode}.
     *
     * @param products       lista de productos filtrados a mostrar
     * @param onProductAdded callback invocado al pulsar una tarjeta o su botón circular {@code (+)}
     */
    public void setProducts(List<Product> products, Consumer<Product> onProductAdded) {
        tilePane.getChildren().clear();

        if (products == null || products.isEmpty()) {
            setFitToHeight(true);
            setContent(emptyStatePane);
            return;
        }

        setFitToHeight(false);
        if (getContent() != tilePane) {
            setContent(tilePane);
        }

        for (Product product : products) {
            ProductCardNode cardNode = new ProductCardNode(product, onProductAdded);
            tilePane.getChildren().add(cardNode);
        }

        if (getViewportBounds() != null && getViewportBounds().getWidth() > 0) {
            updateResponsiveColumns(getViewportBounds().getWidth());
        }
    }

    private void updateResponsiveColumns(double viewportWidth) {
        double horizontalInsets = GRID_PADDING.getLeft() + GRID_PADDING.getRight();
        double usableWidth = Math.max(MIN_TILE_WIDTH, viewportWidth - horizontalInsets - 4.0);

        int columns = Math.max(1, (int) Math.floor((usableWidth + HGAP) / (MIN_TILE_WIDTH + HGAP)));
        double computedTileWidth = Math.floor((usableWidth - (columns - 1) * HGAP) / columns);

        tilePane.setPrefColumns(columns);
        tilePane.setPrefTileWidth(Math.max(MIN_TILE_WIDTH, computedTileWidth));
    }

    private VBox buildEmptySearchState() {
        VBox box = new VBox(10.0);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(64.0, 24.0, 64.0, 24.0));
        box.getStyleClass().add("catalog-empty-state");

        SVGPath searchIcon = new SVGPath();
        searchIcon.setContent("M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16z M21 21l-4.35-4.35");
        searchIcon.setFill(Color.TRANSPARENT);
        searchIcon.setStroke(Color.web("#94A3B8"));
        searchIcon.setStrokeWidth(1.9);
        searchIcon.setStrokeLineCap(StrokeLineCap.ROUND);
        searchIcon.setStrokeLineJoin(StrokeLineJoin.ROUND);
        searchIcon.setScaleX(1.25);
        searchIcon.setScaleY(1.25);

        StackPane iconCircle = new StackPane(searchIcon);
        iconCircle.setPrefSize(52.0, 52.0);
        iconCircle.setMaxSize(52.0, 52.0);
        iconCircle.getStyleClass().add("catalog-empty-icon");

        Label lblTitle = new Label("Sin coincidencias en esta sección");
        lblTitle.getStyleClass().add("catalog-empty-title");

        Label lblSub = new Label("Prueba con otro término de búsqueda o cambia de pestaña en el navegador superior.");
        lblSub.getStyleClass().add("catalog-empty-subtitle");
        lblSub.setWrapText(true);

        box.getChildren().addAll(iconCircle, lblTitle, lblSub);
        return box;
    }

    public TilePane getTilePane() {
        return tilePane;
    }
}
