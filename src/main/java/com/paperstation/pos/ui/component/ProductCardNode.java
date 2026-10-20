package com.paperstation.pos.ui.component;

import com.paperstation.pos.model.Category;
import com.paperstation.pos.model.Product;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.util.Duration;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Tarjeta visual de producto ({@code ProductCardNode}) para la grilla del catálogo POS.
 *
 * <p>Diseñada con intención bajo los principios de PaperStation:</p>
 * <ul>
 *   <li>Superficie blanca pura ({@code #FFFFFF}) con bordes redondeados a {@code 12px}.</li>
 *   <li>Sombra de caída ({@link DropShadow}) gaussiana de baja opacidad calibrada para dar
 *       profundidad real sin ensuciar el lienzo.</li>
 *   <li>Jerarquía tipográfica clara: título en negrita, subtítulo descriptivo respirable
 *       y precio tabular alineado en la base.</li>
 *   <li>Botón circular oscuro {@code (+)} en la esquina inferior derecha con respuesta táctil
 *       a estados {@code hover} y {@code pressed}.</li>
 * </ul>
 */
public class ProductCardNode extends VBox {

    private static final NumberFormat CURRENCY_FORMAT =
            NumberFormat.getCurrencyInstance(Locale.of("es", "MX"));

    private final Product product;
    private final Button btnAdd;

    // Sombras calibradas (reposo, elevación en hover y compresión en press)
    private final DropShadow restingShadow = new DropShadow(
            BlurType.GAUSSIAN,
            Color.rgb(15, 23, 42, 0.065),
            14.0,
            0.0,
            0.0,
            4.0
    );

    private final DropShadow hoverShadow = new DropShadow(
            BlurType.GAUSSIAN,
            Color.rgb(15, 23, 42, 0.12),
            20.0,
            0.0,
            0.0,
            7.0
    );

    private final DropShadow pressedShadow = new DropShadow(
            BlurType.GAUSSIAN,
            Color.rgb(15, 23, 42, 0.045),
            8.0,
            0.0,
            0.0,
            2.0
    );

    public ProductCardNode(Product product, Consumer<Product> onAdd) {
        this.product = Objects.requireNonNull(product, "product");
        Consumer<Product> actionHandler = onAdd != null ? onAdd : p -> {};

        getStyleClass().add("product-card-node");
        setPadding(new Insets(16.0, 16.0, 14.0, 16.0));
        setSpacing(8.0);
        setPrefWidth(212.0);
        setMinWidth(188.0);
        setMaxWidth(Double.MAX_VALUE);
        setPrefHeight(172.0);
        setMinHeight(168.0);
        setCursor(Cursor.HAND);
        setEffect(restingShadow);

        // 1. Fila superior: Ícono temático del artículo + código SKU discreto
        HBox topRow = buildTopMetaRow(product);

        // 2. Bloque tipográfico: Nombre en negrita + subtítulo descriptivo más pequeño
        VBox textBlock = new VBox(4.0);
        textBlock.setPadding(new Insets(2.0, 0, 2.0, 0));

        Label lblName = new Label(product.name());
        lblName.getStyleClass().add("product-card-title");
        lblName.setWrapText(true);
        lblName.setMinHeight(38.0);
        lblName.setMaxHeight(42.0);

        Label lblSubtitle = new Label(product.description());
        lblSubtitle.getStyleClass().add("product-card-subtitle");
        lblSubtitle.setWrapText(true);
        lblSubtitle.setMinHeight(30.0);
        lblSubtitle.setMaxHeight(34.0);

        textBlock.getChildren().addAll(lblName, lblSubtitle);

        // Espaciador elástico para anclar el pie de tarjeta de forma uniforme
        Region verticalSpacer = new Region();
        VBox.setVgrow(verticalSpacer, Priority.ALWAYS);

        // 3. Pie de tarjeta: Precio + Botón circular oscuro (+) en esquina inferior derecha
        HBox footerRow = new HBox(10.0);
        footerRow.setAlignment(Pos.BOTTOM_LEFT);
        footerRow.setPadding(new Insets(4.0, 0, 0, 0));

        Label lblPrice = new Label(CURRENCY_FORMAT.format(product.price()));
        lblPrice.getStyleClass().add("product-card-price");

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);

        this.btnAdd = buildCircularAddButton(actionHandler);

        footerRow.getChildren().addAll(lblPrice, footerSpacer, btnAdd);

        getChildren().addAll(topRow, textBlock, verticalSpacer, footerRow);

        setupInteractions(actionHandler);
    }

    private HBox buildTopMetaRow(Product item) {
        HBox row = new HBox(8.0);
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane iconBadge = createCategoryIconBadge(item);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblSku = new Label("#" + item.id());
        lblSku.getStyleClass().add("product-card-sku");

        row.getChildren().addAll(iconBadge, spacer, lblSku);
        return row;
    }

    private StackPane createCategoryIconBadge(Product item) {
        SVGPath icon = new SVGPath();
        icon.setContent(resolveSvgPath(item.iconName()));
        icon.setFill(Color.TRANSPARENT);

        boolean isCyber = item.category() == Category.CIBERCAFE
                || item.category() == Category.COPIAS_E_IMPRESIONES;

        icon.setStroke(isCyber ? Color.web("#E11D48") : Color.web("#0F172A"));
        icon.setStrokeWidth(1.65);
        icon.setStrokeLineCap(StrokeLineCap.ROUND);
        icon.setStrokeLineJoin(StrokeLineJoin.ROUND);
        icon.setScaleX(0.82);
        icon.setScaleY(0.82);

        StackPane badge = new StackPane(icon);
        badge.setPrefSize(30.0, 30.0);
        badge.setMinSize(30.0, 30.0);
        badge.setMaxSize(30.0, 30.0);
        badge.getStyleClass().add("product-card-icon-badge");
        if (isCyber) {
            badge.getStyleClass().add("product-card-icon-badge-cyber");
        }
        return badge;
    }

    private Button buildCircularAddButton(Consumer<Product> actionHandler) {
        SVGPath plusGlyph = new SVGPath();
        plusGlyph.setContent("M12 6v12M6 12h12");
        plusGlyph.setFill(Color.TRANSPARENT);
        plusGlyph.setStroke(Color.WHITE);
        plusGlyph.setStrokeWidth(2.2);
        plusGlyph.setStrokeLineCap(StrokeLineCap.ROUND);
        plusGlyph.setScaleX(0.85);
        plusGlyph.setScaleY(0.85);

        Button button = new Button();
        button.setGraphic(plusGlyph);
        button.getStyleClass().add("product-card-add-btn");
        button.setPrefSize(34.0, 34.0);
        button.setMinSize(34.0, 34.0);
        button.setMaxSize(34.0, 34.0);
        button.setFocusTraversable(false);
        Tooltip.install(button, new Tooltip("Agregar " + product.name()));

        // Micro-interacción en hover y pressed sobre el botón circular (+)
        button.setOnMouseEntered(e -> animateButtonScale(button, 1.08));
        button.setOnMouseExited(e -> animateButtonScale(button, 1.0));
        button.setOnMousePressed(e -> {
            animateButtonScale(button, 0.92);
            e.consume();
        });
        button.setOnMouseReleased(e -> {
            animateButtonScale(button, button.isHover() ? 1.08 : 1.0);
            e.consume();
        });

        button.setOnAction(e -> {
            actionHandler.accept(product);
            e.consume();
        });

        return button;
    }

    private void setupInteractions(Consumer<Product> actionHandler) {
        setOnMouseEntered(e -> setEffect(hoverShadow));
        setOnMouseExited(e -> {
            setEffect(restingShadow);
            setTranslateY(0.0);
        });
        setOnMousePressed(e -> {
            setEffect(pressedShadow);
            setTranslateY(1.0);
            animateButtonScale(btnAdd, 0.93);
        });
        setOnMouseReleased(e -> {
            setEffect(isHover() ? hoverShadow : restingShadow);
            setTranslateY(0.0);
            animateButtonScale(btnAdd, 1.0);
        });
        setOnMouseClicked(e -> actionHandler.accept(product));
    }

    private void animateButtonScale(Button target, double scale) {
        ScaleTransition st = new ScaleTransition(Duration.millis(110), target);
        st.setToX(scale);
        st.setToY(scale);
        st.play();
    }

    private String resolveSvgPath(String iconName) {
        if (iconName == null) return "M4 4h16v16H4z";
        return switch (iconName.toLowerCase(Locale.ROOT)) {
            case "notebook" -> "M6 3h11a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z M9 3v18 M4 7h2 M4 12h2 M4 17h2";
            case "pen", "marker" -> "M17 3a2.83 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z M15 5l4 4";
            case "glue" -> "M9 3h6v4H9z M8 7h8a1 1 0 0 1 1 1v11a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V8a1 1 0 0 1 1-1z M7 13h10";
            case "scissors" -> "M6 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M6 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M20 4L8.12 15.88 M14.47 14.48L20 20 M8.12 8.12L12 12";
            case "palette" -> "M12 2a10 10 0 0 0 0 20 2.5 2.5 0 0 0 2.5-2.5c0-.6-.23-1.15-.6-1.58-.38-.43-.6-.98-.6-1.58A2.5 2.5 0 0 1 15.8 14H17a5 5 0 0 0 5-5c0-3.87-4.48-7-10-7z M7.5 10.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2z M11.5 7.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2z M16.5 9.5a1 1 0 1 0 0-2 1 1 0 0 0 0 2z";
            case "paper", "copy" -> "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M8 13h8 M8 17h6";
            case "folder" -> "M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z";
            case "printer" -> "M6 9V2h12v7 M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2 M6 14h12v8H6z";
            case "computer" -> "M3 4h18a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z M8 21h8 M12 17v4";
            case "scanner" -> "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2 M3 12h18 M5 16h14a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-2a1 1 0 0 1 1-1z";
            case "binding" -> "M7 4h11a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M3 7h4 M3 11h4 M3 15h4 M3 19h4";
            case "photo" -> "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z M8.5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z M21 15l-5-5L5 21";
            default -> "M12 2L2 7l10 5 10-5-10-5z M2 17l10 5 10-5 M2 12l10 5 10-5";
        };
    }

    public Product getProduct() {
        return product;
    }

    public Button getBtnAdd() {
        return btnAdd;
    }
}
