package com.paperstation.pos.ui.component;

import com.paperstation.pos.model.CartItem;
import com.paperstation.pos.model.Order;
import com.paperstation.pos.service.CartService;
import com.paperstation.pos.service.CartTotals;
import com.paperstation.pos.service.DiscountPolicy;
import com.paperstation.pos.service.ReceiptFormatter;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private static final DateTimeFormatter ORDER_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.of("es", "MX"));

    private final ReceiptFormatter receiptFormatter = new ReceiptFormatter();
    private final ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private CartService cartService;
    private Timeline headerClock;

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
    private final Label lblItemsSummary = new Label("0 partidas");
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

    // Listeners almacenados como referencias para desregistrarlos limpiamente y evitar fugas de memoria
    private final ChangeListener<CartTotals> totalsListener = (obs, oldVal, newVal) -> refreshSummaryState();
    private final ListChangeListener<CartItem> itemsListener = change -> refreshSummaryState();
    private final ChangeListener<Boolean> parkedOrderListener = (obs, oldVal, hasParked) -> {
        btnHold.setText(Boolean.TRUE.equals(hasParked) ? "Recuperar" : "Espera");
        refreshSummaryState();
    };
    private final ChangeListener<Number> orderSequenceListener = (obs, oldVal, newVal) -> updateOrderNumberLabel();

    private Consumer<String> statusConsumer = msg -> {};
    private Runnable afterActionFocusCallback = () -> {};
    private String cashierName = "Admin";

    public CheckoutPanel() {
        this(null);
    }

    public CheckoutPanel(CartService cartService) {
        getStyleClass().add("checkout-panel");
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
        if (cartService != null) {
            bindCartService(cartService);
        } else {
            updateOrderNumberLabel();
        }
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
            private final Label lbl = new Label();

            {
                lbl.getStyleClass().add("cell-product-name");
                setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    lbl.setText(name);
                    setGraphic(lbl);
                    setText(null);
                }
            }
        });

        // Columna: Cantidad con ajuste rápido (- qty +) enlazada reactivamente a item.quantity()
        colQty.setMinWidth(82.0);
        colQty.setMaxWidth(90.0);
        colQty.setPrefWidth(84.0);
        colQty.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
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
                setAlignment(Pos.CENTER);

                btnMinus.setOnAction(e -> {
                    CartItem item = getItem();
                    if (item != null && cartService != null) {
                        try {
                            cartService.updateQuantity(item, item.getQuantity() - 1);
                        } catch (IllegalArgumentException ex) {
                            statusConsumer.accept(ex.getMessage());
                        }
                    }
                });
                btnPlus.setOnAction(e -> {
                    CartItem item = getItem();
                    if (item != null && cartService != null) {
                        try {
                            cartService.updateQuantity(item, item.getQuantity() + 1);
                        } catch (IllegalArgumentException ex) {
                            statusConsumer.accept(ex.getMessage());
                        }
                    }
                });
            }

            @Override
            protected void updateItem(CartItem item, boolean empty) {
                super.updateItem(item, empty);
                lblQty.textProperty().unbind();
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    lblQty.textProperty().bind(item.quantity().asString());
                    setGraphic(stepper);
                }
            }
        });

        // Columna: Precio Unitario (clase CSS añadida una sola vez en el bloque inicializador)
        colUnitPrice.setMinWidth(70.0);
        colUnitPrice.setMaxWidth(85.0);
        colUnitPrice.setPrefWidth(76.0);
        colUnitPrice.setCellValueFactory(data ->
                new SimpleStringProperty(ReceiptFormatter.formatCurrency(data.getValue().getUnitPrice())));
        colUnitPrice.setCellFactory(col -> new TableCell<>() {
            {
                getStyleClass().add("cell-unit-price");
                setAlignment(Pos.CENTER_RIGHT);
            }

            @Override
            protected void updateItem(String price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? null : price);
            }
        });

        // Columna: Importe reactivo + botón sutil de quitar partida
        colSubtotal.setMinWidth(95.0);
        colSubtotal.setMaxWidth(115.0);
        colSubtotal.setPrefWidth(102.0);
        colSubtotal.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
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
                setAlignment(Pos.CENTER_RIGHT);

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
                lblLineTotal.textProperty().unbind();
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    lblLineTotal.textProperty().bind(Bindings.createStringBinding(
                            () -> ReceiptFormatter.formatCurrency(item.getTotal()),
                            item.quantity()
                    ));
                    setGraphic(box);
                }
            }
        });

        cartTable.getColumns().setAll(colProduct, colQty, colUnitPrice, colSubtotal);

        VBox wrapper = new VBox(cartTable);
        wrapper.getStyleClass().add("checkout-table-wrapper");
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

        VBox breakdown = new VBox(7.0);

        HBox rowSubtotal = createBreakdownRow("Subtotal", lblSubtotalValue);
        HBox rowDiscount = createBreakdownRow("Descuento", lblDiscountValue);
        HBox rowTax = createBreakdownRow("IVA (16%)", lblTaxValue);

        breakdown.getChildren().addAll(rowSubtotal, rowDiscount, rowTax);

        Region totalDivider = createHorizontalDivider();

        HBox rowTotal = new HBox();
        rowTotal.setAlignment(Pos.BASELINE_LEFT);
        rowTotal.setPadding(new Insets(4.0, 0, 2.0, 0));

        Label lblTotalHeading = new Label("Total");
        lblTotalHeading.getStyleClass().add("checkout-total-label");

        Region totalSpacer = new Region();
        HBox.setHgrow(totalSpacer, Priority.ALWAYS);

        lblTotalValue.getStyleClass().add("checkout-total-amount");

        rowTotal.getChildren().addAll(lblTotalHeading, totalSpacer, lblTotalValue);

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

        btnCancel.getStyleClass().addAll("pos-action-btn", "pos-btn-cancel");
        btnHold.getStyleClass().addAll("pos-action-btn", "pos-btn-utility");
        btnDiscount.getStyleClass().addAll("pos-action-btn", "pos-btn-utility");
        btnTicket.getStyleClass().addAll("pos-action-btn", "pos-btn-utility");
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
     * Enlaza el panel reactivamente a la instancia de {@link CartService},
     * desregistrando previamente cualquier listener anterior para evitar fugas de memoria.
     */
    public void bindCartService(CartService service) {
        unbindCurrentCartService();
        if (service == null) return;
        this.cartService = service;

        cartTable.setItems(cartService.getItems());

        cartService.totalsProperty().addListener(totalsListener);
        cartService.getItems().addListener(itemsListener);
        cartService.hasParkedOrderProperty().addListener(parkedOrderListener);
        cartService.currentOrderSequenceProperty().addListener(orderSequenceListener);

        btnHold.setText(cartService.hasParkedOrder() ? "Recuperar" : "Espera");
        updateOrderNumberLabel();
        refreshSummaryState();
    }

    private void unbindCurrentCartService() {
        if (this.cartService != null) {
            this.cartService.totalsProperty().removeListener(totalsListener);
            this.cartService.getItems().removeListener(itemsListener);
            this.cartService.hasParkedOrderProperty().removeListener(parkedOrderListener);
            this.cartService.currentOrderSequenceProperty().removeListener(orderSequenceListener);
        }
    }

    private void refreshSummaryState() {
        if (cartService == null) return;
        int lineCount = cartService.getLineCount();
        int totalUnits = cartService.getItemCount();
        if (lineCount == 0) {
            lblItemsSummary.setText("0 partidas");
        } else {
            lblItemsSummary.setText(lineCount + (lineCount == 1 ? " partida" : " partidas")
                    + " (" + totalUnits + (totalUnits == 1 ? " ud." : " uds.") + ")");
        }

        CartTotals totals = cartService.getTotals();
        if (totals != null) {
            lblSubtotalValue.setText(ReceiptFormatter.formatCurrency(totals.subtotal()));
            BigDecimal disc = totals.discount();
            lblDiscountValue.setText(disc.signum() > 0
                    ? "−" + ReceiptFormatter.formatCurrency(disc)
                    : ReceiptFormatter.formatCurrency(BigDecimal.ZERO));
            lblTaxValue.setText(ReceiptFormatter.formatCurrency(totals.tax()));
            lblTotalValue.setText(ReceiptFormatter.formatCurrency(totals.total()));
        }

        boolean empty = cartService.isEmpty();
        btnCancel.setDisable(empty);
        btnDiscount.setDisable(empty);
        btnTicket.setDisable(empty);
        btnCharge.setDisable(empty);
        btnHold.setDisable(empty && !cartService.hasParkedOrder());
    }

    public void handleCancelAction() {
        if (cartService == null || cartService.getItems().isEmpty()) return;

        int seq = cartService.getCurrentOrderSequence();
        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "¿Cancelar la orden #" + String.format("%05d", seq) + " y vaciar las partidas?",
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

        try {
            if (!cartService.isEmpty()) {
                int parkedCount = cartService.parkCurrentOrder();
                statusConsumer.accept("Orden puesta en espera (" + parkedCount + " partidas).");
            } else if (cartService.hasParkedOrder()) {
                cartService.restoreParkedOrder();
                statusConsumer.accept("Orden en espera recuperada con su descuento.");
            }
        } catch (IllegalArgumentException ex) {
            statusConsumer.accept("Error al recuperar orden en espera: " + ex.getMessage());
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
            } catch (IllegalArgumentException ex) {
                statusConsumer.accept("Porcentaje inválido.");
            }
            afterActionFocusCallback.run();
        });
    }

    public void handleTicketPreviewAction() {
        if (cartService == null || cartService.getItems().isEmpty()) return;

        String registerId = lblRegisterId.getText();
        String orderNumberText = lblOrderNumber.getText();
        LocalDateTime now = LocalDateTime.now();
        var itemsSnapshot = cartService.getItems().stream().toList();
        CartTotals totalsSnapshot = cartService.getTotals();

        Task<String> previewTask = new Task<>() {
            @Override
            protected String call() {
                return receiptFormatter.formatPreTicket(registerId, orderNumberText, now, itemsSnapshot, totalsSnapshot);
            }
        };

        previewTask.setOnSucceeded(e -> Platform.runLater(() -> {
            Alert ticketDialog = new Alert(Alert.AlertType.INFORMATION);
            ticketDialog.setTitle("Vista Previa de Ticket");
            ticketDialog.setHeaderText("Pre-ticket · " + orderNumberText);
            ticketDialog.setContentText(previewTask.getValue());
            ticketDialog.showAndWait();
            afterActionFocusCallback.run();
        }));

        previewTask.setOnFailed(e -> Platform.runLater(() ->
                statusConsumer.accept("Error al generar vista previa del ticket.")));

        asyncExecutor.submit(previewTask);
    }

    public void handleChargeAction() {
        if (cartService == null || cartService.getItems().isEmpty()) return;

        int currentOrder = cartService.getCurrentOrderSequence();
        Order order = cartService.checkout(cashierName);
        String chargedAmount = ReceiptFormatter.formatCurrency(order.total());
        String registerId = lblRegisterId.getText();

        Task<String> receiptTask = new Task<>() {
            @Override
            protected String call() {
                return receiptFormatter.formatOrderReceipt(registerId, order);
            }
        };

        receiptTask.setOnSucceeded(e -> Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Cobro Registrado");
            alert.setHeaderText("Orden #" + String.format("%05d", currentOrder) + " cobrada exitosamente");
            alert.setContentText("Folio: " + order.orderId() + "\nImporte total recibido: " + chargedAmount);
            alert.showAndWait();

            statusConsumer.accept("Venta #" + String.format("%05d", currentOrder) + " finalizada (" + chargedAmount + ").");
            afterActionFocusCallback.run();
        }));

        receiptTask.setOnFailed(e -> Platform.runLater(() ->
                statusConsumer.accept("Error al procesar recibo de cobro.")));

        asyncExecutor.submit(receiptTask);
    }

    private void updateOrderNumberLabel() {
        int seq = cartService != null ? cartService.getCurrentOrderSequence() : 104;
        lblOrderNumber.setText(String.format("Orden #%05d", seq));
    }

    private void initHeaderClock() {
        headerClock = new Timeline(
                new KeyFrame(Duration.ZERO, e -> lblOrderDate.setText(LocalDateTime.now().format(ORDER_DATE_FORMAT))),
                new KeyFrame(Duration.seconds(30))
        );
        headerClock.setCycleCount(Animation.INDEFINITE);
        headerClock.play();
    }

    /**
     * Libera recursos activos (reloj Timeline, listeners y executor asíncrono) al cerrar la vista.
     */
    public void dispose() {
        if (headerClock != null) {
            headerClock.stop();
        }
        unbindCurrentCartService();
        asyncExecutor.shutdownNow();
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
