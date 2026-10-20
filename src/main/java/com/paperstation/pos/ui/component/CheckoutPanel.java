package com.paperstation.pos.ui.component;

import com.paperstation.pos.model.CartItem;
import com.paperstation.pos.model.Order;
import com.paperstation.pos.service.CartService;
import com.paperstation.pos.service.CartTotals;
import com.paperstation.pos.service.DiscountPolicy;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Panel izquierdo del POS (Carrito y Cobro).
 * Construido bajo las directrices de diseño intencional de PaperStation:
 * - Fondo blanco puro (#FFFFFF) con estructura basada en divisores sutiles.
 * - Encabezado de metadatos (Orden, Caja, Fecha) sin etiquetas redundantes.
 * - Tabla reactiva enlazada al ObservableList<CartItem> de CartService con estado vacío ilustrado.
 * - Escala tipográfica jerárquica con peso visual dominante en "Total".
 * - Barra inferior de 5 acciones codificadas por color semántico (Cancelar, Espera, Descuento, Ticket, Cobrar).
 */
public class CheckoutPanel extends VBox {

    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.of("es", "MX"));
    private static final DateTimeFormatter ORDER_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.of("es", "MX"));

    private CartService cartService;
    private int orderSequence = 104;
    private final List<CartItem> parkedOrder = new ArrayList<>();

    // Metadatos del encabezado
    private final Label lblOrderNumber = new Label();
    private final Label lblRegisterId = new Label("Caja 01");
    private final Label lblOrderDate = new Label();

    // Tabla de partidas
    private final TableView<CartItem> cartTable = new TableView<>();
    private final TableColumn<CartItem, String> colProduct = new TableColumn<>("Artículo");
    private final TableColumn<CartItem, CartItem> colQty = new TableColumn<>("Cant.");
    private final TableColumn<CartItem, String> colUnitPrice = new TableColumn<>("P. Unit.");
    private final TableColumn<CartItem, CartItem> colSubtotal = new TableColumn<>("Importe");

    // Resumen financiero
    private final Label lblItemsSummary = new Label("0 artículos");
    private final Label lblSubtotalValue = new Label("$0.00");
    private final Label lblDiscountValue = new Label("$0.00");
    private final Label lblTaxValue = new Label("$0.00");
    private final Label lblTotalValue = new Label("$0.00");

    // 5 botones de acción
    private final Button btnCancel = new Button("Cancelar");
    private final Button btnHold = new Button("Espera");
    private final Button btnDiscount = new Button("Descuento");
    private final Button btnTicket = new Button("Ticket");
    private final Button btnCharge = new Button("Cobrar");

    private Consumer<String> statusConsumer = msg -> {};
    private Runnable afterActionFocusCallback = () -> {};
    private String cashierName = "Admin";

    public CheckoutPanel() {
        this(new CartService());
    }

    public CheckoutPanel(CartService cartService) {
        getStyleClass().add("checkout-panel");
        setStyle("-fx-background-color: #FFFFFF;");
        setPrefWidth(460.0);
        setMinWidth(410.0);
        setMaxWidth(520.0);
        setSpacing(0);

        HBox headerStrip = buildOrderHeader();
        Region headerDivider = createHorizontalDivider();
        VBox tableContainer = buildTableSection();
        Region summaryTopDivider = createHorizontalDivider();
        VBox financialSection = buildFinancialSummaryAndActions();

        VBox.setVgrow(tableContainer, Priority.ALWAYS);

        getChildren().addAll(
                headerStrip,
                headerDivider,
                tableContainer,
                summaryTopDivider,
                financialSection
        );

        initHeaderClock();
        bindCartService(cartService);
    }

    /**
     * 2. Encabezado de la orden: Muestra metadatos (Orden, Caja, Fecha)
     * usando divisores sutiles y evitando jerarquías visuales redundantes.
     */
    private HBox buildOrderHeader() {
        HBox header = new HBox(12.0);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16.0, 20.0, 14.0, 20.0));
        header.getStyleClass().add("checkout-order-header");

        updateOrderNumberLabel();
        lblOrderNumber.getStyleClass().add("meta-order-id");

        Region divider1 = createVerticalDivider();

        lblRegisterId.getStyleClass().add("meta-register");

        Region divider2 = createVerticalDivider();

        lblOrderDate.setText(LocalDateTime.now().format(ORDER_DATE_FORMAT));
        lblOrderDate.getStyleClass().add("meta-date");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        lblItemsSummary.getStyleClass().add("meta-item-count");

        header.getChildren().addAll(
                lblOrderNumber,
                divider1,
                lblRegisterId,
                divider2,
                lblOrderDate,
                spacer,
                lblItemsSummary
        );
        return header;
    }

    /**
     * 3. Tabla de partidas reactiva enlazada al ObservableList<CartItem> de CartService
     * con estado vacío ("El carrito está vacío") e ícono vectorial claro.
     */
    @SuppressWarnings("unchecked")
    private VBox buildTableSection() {
        cartTable.getStyleClass().add("checkout-table");
        cartTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cartTable.setFixedCellSize(46.0);
        cartTable.setPlaceholder(buildEmptyCartState());
        VBox.setVgrow(cartTable, Priority.ALWAYS);

        // Columna: Producto
        colProduct.setMinWidth(155.0);
        colProduct.setPrefWidth(185.0);
        colProduct.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        colProduct.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label lbl = new Label(name);
                    lbl.getStyleClass().add("cell-product-name");
                    setGraphic(lbl);
                    setText(null);
                    setAlignment(Pos.CENTER_LEFT);
                }
            }
        });

        // Columna: Cantidad con ajuste rápido (- qty +)
        colQty.setMinWidth(82.0);
        colQty.setMaxWidth(90.0);
        colQty.setPrefWidth(84.0);
        colQty.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        colQty.setCellFactory(col -> new TableCell<>() {
            private final Button btnMinus = new Button("−");
            private final Label lblQty = new Label();
            private final Button btnPlus = new Button("+");
            private final HBox stepper = new HBox(4.0, btnMinus, lblQty, btnPlus);

            {
                stepper.setAlignment(Pos.CENTER);
                btnMinus.getStyleClass().add("qty-step-btn");
                btnPlus.getStyleClass().add("qty-step-btn");
                lblQty.getStyleClass().add("cell-qty-value");

                btnMinus.setOnAction(e -> {
                    CartItem item = getItem();
                    if (item != null && cartService != null) {
                        cartService.updateQuantity(item, item.getQuantity() - 1);
                    }
                });
                btnPlus.setOnAction(e -> {
                    CartItem item = getItem();
                    if (item != null && cartService != null) {
                        cartService.updateQuantity(item, item.getQuantity() + 1);
                    }
                });
            }

            @Override
            protected void updateItem(CartItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    lblQty.setText(String.valueOf(item.getQuantity()));
                    setGraphic(stepper);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // Columna: Precio Unitario
        colUnitPrice.setMinWidth(70.0);
        colUnitPrice.setMaxWidth(85.0);
        colUnitPrice.setPrefWidth(76.0);
        colUnitPrice.setCellValueFactory(data ->
                new SimpleStringProperty(CURRENCY_FORMAT.format(data.getValue().getUnitPrice())));
        colUnitPrice.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(price);
                    getStyleClass().add("cell-unit-price");
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        // Columna: Importe + botón sutil de quitar partida
        colSubtotal.setMinWidth(95.0);
        colSubtotal.setMaxWidth(115.0);
        colSubtotal.setPrefWidth(102.0);
        colSubtotal.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        colSubtotal.setCellFactory(col -> new TableCell<>() {
            private final Label lblLineTotal = new Label();
            private final Button btnDelete = new Button("×");
            private final Region spacer = new Region();
            private final HBox box = new HBox(6.0, spacer, lblLineTotal, btnDelete);

            {
                HBox.setHgrow(spacer, Priority.ALWAYS);
                box.setAlignment(Pos.CENTER_RIGHT);
                lblLineTotal.getStyleClass().add("cell-line-total");
                btnDelete.getStyleClass().add("cell-remove-btn");
                btnDelete.setOnAction(e -> {
                    CartItem item = getItem();
                    if (item != null && cartService != null) {
                        cartService.removeProduct(item);
                        statusConsumer.accept("Partida eliminada: " + item.getProductName());
                    }
                });
            }

            @Override
            protected void updateItem(CartItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    lblLineTotal.setText(CURRENCY_FORMAT.format(item.getTotal()));
                    setGraphic(box);
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        });

        cartTable.getColumns().setAll(colProduct, colQty, colUnitPrice, colSubtotal);

        VBox wrapper = new VBox(cartTable);
        wrapper.setStyle("-fx-background-color: #FFFFFF;");
        return wrapper;
    }

    /**
     * Construye el estado vacío ("El carrito está vacío") con un ícono SVG claro y minimalista.
     */
    private VBox buildEmptyCartState() {
        VBox emptyState = new VBox(10.0);
        emptyState.setAlignment(Pos.CENTER);
        emptyState.setPadding(new Insets(36.0, 28.0, 36.0, 28.0));
        emptyState.getStyleClass().add("checkout-empty-state");

        // Ícono vectorial claro de carrito / bandeja de compra
        SVGPath cartOutline = new SVGPath();
        cartOutline.setContent("M3 4h2l2.4 12.2a2 2 0 0 0 2 1.6h9.8a2 2 0 0 0 2-1.6L23 7H6.2 M10 21a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z M19 21a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z");
        cartOutline.setFill(Color.TRANSPARENT);
        cartOutline.setStroke(Color.web("#94A3B8"));
        cartOutline.setStrokeWidth(1.8);
        cartOutline.setStrokeLineCap(StrokeLineCap.ROUND);
        cartOutline.setStrokeLineJoin(StrokeLineJoin.ROUND);
        cartOutline.setScaleX(1.35);
        cartOutline.setScaleY(1.35);

        StackPane iconHolder = new StackPane(cartOutline);
        iconHolder.setPrefSize(52.0, 52.0);
        iconHolder.setMaxSize(52.0, 52.0);
        iconHolder.getStyleClass().add("checkout-empty-icon-holder");

        Label lblEmptyTitle = new Label("El carrito está vacío");
        lblEmptyTitle.getStyleClass().add("checkout-empty-title");

        Label lblEmptyHint = new Label("Escanea un código de barras o selecciona un artículo del catálogo");
        lblEmptyHint.getStyleClass().add("checkout-empty-subtitle");
        lblEmptyHint.setWrapText(true);

        emptyState.getChildren().addAll(iconHolder, lblEmptyTitle, lblEmptyHint);
        return emptyState;
    }

    /**
     * 4. Resumen financiero y acciones:
     * - Escala tipográfica clara donde el "Total" es el elemento con mayor peso visual.
     * - HBox inferior con 5 botones de acción (Cancelar, Espera, Descuento, Ticket, Cobrar).
     */
    private VBox buildFinancialSummaryAndActions() {
        VBox container = new VBox(16.0);
        container.setPadding(new Insets(16.0, 20.0, 20.0, 20.0));
        container.getStyleClass().add("checkout-footer-section");

        // Desglose financiero
        VBox breakdown = new VBox(7.0);

        HBox rowSubtotal = createBreakdownRow("Subtotal", lblSubtotalValue);
        HBox rowDiscount = createBreakdownRow("Descuento", lblDiscountValue);
        HBox rowTax = createBreakdownRow("IVA (16%)", lblTaxValue);

        breakdown.getChildren().addAll(rowSubtotal, rowDiscount, rowTax);

        Region totalDivider = createHorizontalDivider();

        // Renglón principal: Total con el mayor peso visual del panel
        HBox rowTotal = new HBox();
        rowTotal.setAlignment(Pos.BASELINE_LEFT);
        rowTotal.setPadding(new Insets(4.0, 0, 2.0, 0));

        Label lblTotalHeading = new Label("Total");
        lblTotalHeading.getStyleClass().add("checkout-total-label");

        Region totalSpacer = new Region();
        HBox.setHgrow(totalSpacer, Priority.ALWAYS);

        lblTotalValue.getStyleClass().add("checkout-total-amount");

        rowTotal.getChildren().addAll(lblTotalHeading, totalSpacer, lblTotalValue);

        // Barra inferior con los 5 botones de acción
        HBox actionsBar = buildActionsBar();

        container.getChildren().addAll(breakdown, totalDivider, rowTotal, actionsBar);
        return container;
    }

    private HBox createBreakdownRow(String labelText, Label valueLabel) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("checkout-summary-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        valueLabel.getStyleClass().add("checkout-summary-value");

        row.getChildren().addAll(lbl, spacer, valueLabel);
        return row;
    }

    private HBox buildActionsBar() {
        HBox bar = new HBox(8.0);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("checkout-actions-bar");

        // Color semántico sin decoraciones innecesarias:
        // Naranja para Cancelar
        btnCancel.getStyleClass().addAll("pos-action-btn", "pos-btn-cancel");
        // Azul oscuro para utilidades (Espera, Descuento, Ticket)
        btnHold.getStyleClass().addAll("pos-action-btn", "pos-btn-utility");
        btnDiscount.getStyleClass().addAll("pos-action-btn", "pos-btn-utility");
        btnTicket.getStyleClass().addAll("pos-action-btn", "pos-btn-utility");
        // Rojo/Coral para Cobrar
        btnCharge.getStyleClass().addAll("pos-action-btn", "pos-btn-charge");

        HBox.setHgrow(btnCharge, Priority.ALWAYS);
        btnCharge.setMaxWidth(Double.MAX_VALUE);

        btnCancel.setOnAction(e -> handleCancelAction());
        btnHold.setOnAction(e -> handleHoldAction());
        btnDiscount.setOnAction(e -> handleDiscountAction());
        btnTicket.setOnAction(e -> handleTicketPreviewAction());
        btnCharge.setOnAction(e -> handleChargeAction());

        bar.getChildren().addAll(btnCancel, btnHold, btnDiscount, btnTicket, btnCharge);
        return bar;
    }

    /**
     * Enlaza el panel reactivamente a la instancia de {@link CartService}.
     */
    public void bindCartService(CartService service) {
        if (service == null) return;
        this.cartService = service;

        cartTable.setItems(cartService.getItems());

        // Un único listener sobre la instantánea exacta (BigDecimal) de totales del servicio.
        cartService.totalsProperty().addListener((obs, oldVal, newVal) -> refreshSummaryState());

        cartService.getItems().addListener((ListChangeListener<CartItem>) change -> {
            refreshSummaryState();
            cartTable.refresh();
        });

        refreshSummaryState();
    }

    private void refreshSummaryState() {
        if (cartService == null) return;
        int totalUnits = cartService.getItemCount();
        lblItemsSummary.setText(totalUnits + (totalUnits == 1 ? " partida" : " partidas"));

        CartTotals totals = cartService.getTotals();
        lblSubtotalValue.setText(CURRENCY_FORMAT.format(totals.subtotal()));
        BigDecimal disc = totals.discount();
        lblDiscountValue.setText(disc.signum() > 0
                ? "−" + CURRENCY_FORMAT.format(disc)
                : CURRENCY_FORMAT.format(BigDecimal.ZERO));
        lblTaxValue.setText(CURRENCY_FORMAT.format(totals.tax()));
        lblTotalValue.setText(CURRENCY_FORMAT.format(totals.total()));

        boolean empty = cartService.isEmpty();
        btnCancel.setDisable(empty);
        btnDiscount.setDisable(empty);
        btnTicket.setDisable(empty);
        btnCharge.setDisable(empty);
        btnHold.setDisable(empty && parkedOrder.isEmpty());
    }

    public void handleCancelAction() {
        if (cartService == null || cartService.getItems().isEmpty()) return;

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "¿Cancelar la orden #" + String.format("%05d", orderSequence) + " y vaciar las partidas?",
                ButtonType.YES,
                ButtonType.NO
        );
        confirm.setTitle("Cancelar Orden");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                cartService.clearCart();
                statusConsumer.accept("Orden cancelada.");
                afterActionFocusCallback.run();
            }
        });
    }

    public void handleHoldAction() {
        if (cartService == null) return;

        if (!cartService.getItems().isEmpty()) {
            parkedOrder.clear();
            parkedOrder.addAll(cartService.getItems());
            cartService.clearCart();
            btnHold.setText("Recuperar");
            refreshSummaryState();
            statusConsumer.accept("Orden puesta en espera (" + parkedOrder.size() + " partidas).");
        } else if (!parkedOrder.isEmpty()) {
            for (CartItem item : parkedOrder) {
                cartService.addProduct(item.product(), item.getQuantity());
            }
            parkedOrder.clear();
            btnHold.setText("Espera");
            refreshSummaryState();
            statusConsumer.accept("Orden en espera recuperada.");
        }
        afterActionFocusCallback.run();
    }

    public void handleDiscountAction() {
        if (cartService == null || cartService.getItems().isEmpty()) return;

        TextInputDialog dialog = new TextInputDialog("10");
        dialog.setTitle("Aplicar Descuento");
        dialog.setHeaderText("Descuento porcentual sobre la orden actual");
        dialog.setContentText("Porcentaje (%):");
        dialog.showAndWait().ifPresent(input -> {
            try {
                BigDecimal pct = new BigDecimal(input.trim());
                cartService.applyDiscount(DiscountPolicy.percentage(pct));
                statusConsumer.accept("Descuento del " + pct + "% aplicado.");
            } catch (IllegalArgumentException ex) { // incluye NumberFormatException y rango 0–100
                statusConsumer.accept("Porcentaje inválido.");
            }
            afterActionFocusCallback.run();
        });
    }

    public void handleTicketPreviewAction() {
        if (cartService == null || cartService.getItems().isEmpty()) return;

        StringBuilder receipt = new StringBuilder();
        receipt.append("PaperStation POS · ").append(lblRegisterId.getText()).append("\n");
        receipt.append(lblOrderNumber.getText()).append("   ").append(lblOrderDate.getText()).append("\n");
        receipt.append("----------------------------------------\n");
        for (CartItem item : cartService.getItems()) {
            receipt.append(String.format("%dx %-22s %10s%n",
                    item.getQuantity(),
                    item.getProductName().length() > 22 ? item.getProductName().substring(0, 22) : item.getProductName(),
                    CURRENCY_FORMAT.format(item.getTotal())));
        }
        receipt.append("----------------------------------------\n");
        receipt.append("Subtotal: ").append(lblSubtotalValue.getText()).append("\n");
        receipt.append("Descuento: ").append(lblDiscountValue.getText()).append("\n");
        receipt.append("IVA (16%): ").append(lblTaxValue.getText()).append("\n");
        receipt.append("TOTAL: ").append(lblTotalValue.getText());

        Alert ticketDialog = new Alert(Alert.AlertType.INFORMATION);
        ticketDialog.setTitle("Vista Previa de Ticket");
        ticketDialog.setHeaderText("Pre-ticket · " + lblOrderNumber.getText());
        ticketDialog.setContentText(receipt.toString());
        ticketDialog.showAndWait();
        afterActionFocusCallback.run();
    }

    public void handleChargeAction() {
        if (cartService == null || cartService.getItems().isEmpty()) return;

        String chargedAmount = CURRENCY_FORMAT.format(cartService.getTotals().total());
        int currentOrder = orderSequence;

        // checkout() congela las partidas en un Order inmutable y vacía el carrito.
        Order order = cartService.checkout(cashierName);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Cobro Registrado");
        alert.setHeaderText("Orden #" + String.format("%05d", currentOrder) + " cobrada exitosamente");
        alert.setContentText("Folio: " + order.orderId() + "\nImporte total recibido: " + chargedAmount);
        alert.showAndWait();

        orderSequence++;
        updateOrderNumberLabel();
        statusConsumer.accept("Venta #" + String.format("%05d", currentOrder) + " finalizada (" + chargedAmount + ").");
        afterActionFocusCallback.run();
    }

    private void updateOrderNumberLabel() {
        lblOrderNumber.setText(String.format("Orden #%05d", orderSequence));
    }

    private void initHeaderClock() {
        Timeline clock = new Timeline(
                new KeyFrame(Duration.ZERO, e -> lblOrderDate.setText(LocalDateTime.now().format(ORDER_DATE_FORMAT))),
                new KeyFrame(Duration.seconds(30))
        );
        clock.setCycleCount(Animation.INDEFINITE);
        clock.play();
    }

    private Region createHorizontalDivider() {
        Region divider = new Region();
        divider.getStyleClass().add("checkout-divider-h");
        divider.setMinHeight(1.0);
        divider.setPrefHeight(1.0);
        divider.setMaxHeight(1.0);
        return divider;
    }

    private Region createVerticalDivider() {
        Region divider = new Region();
        divider.getStyleClass().add("checkout-divider-v");
        divider.setMinWidth(1.0);
        divider.setPrefWidth(1.0);
        divider.setMaxWidth(1.0);
        divider.setMinHeight(14.0);
        divider.setPrefHeight(14.0);
        divider.setMaxHeight(14.0);
        return divider;
    }

    public void setOnStatusMessage(Consumer<String> statusConsumer) {
        this.statusConsumer = statusConsumer != null ? statusConsumer : msg -> {};
    }

    public void setAfterActionFocusCallback(Runnable callback) {
        this.afterActionFocusCallback = callback != null ? callback : () -> {};
    }

    /** Nombre del cajero que se registra en cada {@link Order} generada al cobrar. */
    public void setCashierName(String cashierName) {
        if (cashierName != null && !cashierName.isBlank()) {
            this.cashierName = cashierName;
        }
    }

    public Button getBtnCancel() {
        return btnCancel;
    }

    public Button getBtnCharge() {
        return btnCharge;
    }
}
