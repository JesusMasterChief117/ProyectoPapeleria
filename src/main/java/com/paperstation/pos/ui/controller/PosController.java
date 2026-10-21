package com.paperstation.pos.ui.controller;

import com.paperstation.pos.model.Category;
import com.paperstation.pos.model.Product;
import com.paperstation.pos.service.CartService;
import com.paperstation.pos.service.InventoryService;
import com.paperstation.pos.ui.component.CatalogGrid;
import com.paperstation.pos.ui.component.CheckoutPanel;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controlador principal de la terminal de punto de venta (POS).
 *
 * <p>Coordina el panel superior de navegación (pestañas trapezoidales de alto contraste
 * {@code Destacados}, {@code Papelería}, {@code Cibercafé} y barra de búsqueda redondeada),
 * la grilla responsiva {@link CatalogGrid} con tarjetas {@code ProductCardNode}, y el
 * {@link CheckoutPanel} enlazado a la misma instancia de {@link CartService}.</p>
 */
public class PosController implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(PosController.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM yyyy - HH:mm:ss", Locale.of("es", "MX"));
    private static final String CASHIER_NAME = "Admin";

    private static final List<Category> NAVIGATION_TABS = List.of(
            Category.DESTACADOS,
            Category.PAPELERIA,
            Category.CIBERCAFE
    );

    @FXML private BorderPane rootPane;
    @FXML private Label lblDateTime;
    @FXML private HBox searchPillBox;
    @FXML private TextField txtSearch;
    @FXML private Button btnClearSearch;
    @FXML private HBox categoryBar;
    @FXML private CatalogGrid catalogGrid;
    @FXML private Label lblStatusMessage;

    // Panel izquierdo: carrito y cobro
    @FXML private CheckoutPanel checkoutPanel;

    private final InventoryService inventoryService;
    private final CartService cartService;
    private Category selectedCategory = Category.DESTACADOS;
    private Timeline liveClock;

    /** Constructor por defecto requerido por FXML si no se especifica ControllerFactory. */
    public PosController() {
        this(new InventoryService(), new CartService());
    }

    /** Constructor con inyección de dependencias para pruebas y configuración explícita. */
    public PosController(InventoryService inventoryService, CartService cartService) {
        this.inventoryService = inventoryService != null ? inventoryService : new InventoryService();
        this.cartService = cartService != null ? cartService : new CartService();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initLiveClock();
        initCheckoutPanel();
        initNavigationTabs();
        initSearchBarListeners();
        refreshProductGrid();
        setupKeyboardShortcuts();

        // Foco inicial automático en el buscador/escáner
        txtSearch.requestFocus();
        logger.info("PosController inicializado con éxito.");
    }

    private void initLiveClock() {
        liveClock = new Timeline(new KeyFrame(Duration.ZERO, e -> {
            lblDateTime.setText(LocalDateTime.now().format(DATE_TIME_FORMAT));
        }), new KeyFrame(Duration.seconds(1)));
        liveClock.setCycleCount(Animation.INDEFINITE);
        liveClock.play();
    }

    /**
     * Detiene los temporizadores activos y libera recursos de los subcomponentes al cerrar la aplicación.
     */
    public void dispose() {
        if (liveClock != null) {
            liveClock.stop();
        }
        if (checkoutPanel != null) {
            checkoutPanel.dispose();
        }
    }

    private void initCheckoutPanel() {
        checkoutPanel.bindCartService(cartService);
        checkoutPanel.setCashierName(CASHIER_NAME);
        checkoutPanel.setOnStatusMessage(this::setStatusMessage);
        checkoutPanel.setAfterActionFocusCallback(() -> txtSearch.requestFocus());
    }

    /**
     * Construye la barra de pestañas trapezoidales/asimétricas de alto contraste
     * (Destacados, Papelería, Cibercafé), destacando la pestaña activa en rojo.
     */
    private void initNavigationTabs() {
        categoryBar.getChildren().clear();

        for (Category tabCategory : NAVIGATION_TABS) {
            Button tabBtn = new Button(tabCategory.getDisplayName());
            tabBtn.getStyleClass().add("catalog-tab");
            tabBtn.setFocusTraversable(false);

            if (tabCategory == selectedCategory) {
                tabBtn.getStyleClass().add("catalog-tab-active");
            }

            tabBtn.setOnAction(e -> selectTab(tabCategory));
            categoryBar.getChildren().add(tabBtn);
        }
    }

    private void selectTab(Category category) {
        if (category == null) return;
        this.selectedCategory = category;
        updateNavigationTabsStyle();
        refreshProductGrid();
        setStatusMessage("Sección activa: " + category.getDisplayName());
    }

    private void updateNavigationTabsStyle() {
        for (int i = 0; i < NAVIGATION_TABS.size() && i < categoryBar.getChildren().size(); i++) {
            Category cat = NAVIGATION_TABS.get(i);
            Button btn = (Button) categoryBar.getChildren().get(i);
            btn.getStyleClass().remove("catalog-tab-active");
            if (cat == selectedCategory) {
                btn.getStyleClass().add("catalog-tab-active");
            }
        }
    }

    private void initSearchBarListeners() {
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasText = newVal != null && !newVal.isEmpty();
            if (btnClearSearch != null) {
                btnClearSearch.setVisible(hasText);
                btnClearSearch.setManaged(hasText);
            }
            refreshProductGrid();
        });

        txtSearch.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (searchPillBox != null) {
                if (isNowFocused) {
                    if (!searchPillBox.getStyleClass().contains("search-pill-box-focused")) {
                        searchPillBox.getStyleClass().add("search-pill-box-focused");
                    }
                } else {
                    searchPillBox.getStyleClass().remove("search-pill-box-focused");
                }
            }
        });
    }

    @FXML
    private void onSearchTextChanged() {
        refreshProductGrid();
    }

    @FXML
    private void onClearSearchAction() {
        txtSearch.clear();
        txtSearch.requestFocus();
    }

    @FXML
    private void onSearchAction() {
        String input = txtSearch.getText().trim();
        if (input.isEmpty()) return;

        // Si coincide con el código exacto (id/SKU), agregar inmediatamente al ticket
        Optional<Product> directMatch = inventoryService.findById(input);
        if (directMatch.isPresent()) {
            addProductToCart(directMatch.get());
            txtSearch.clear();
            refreshProductGrid();
            return;
        }

        // Si solo hay un resultado filtrado, agregarlo
        List<Product> matches = inventoryService.search(input, selectedCategory);
        if (matches.size() == 1) {
            addProductToCart(matches.getFirst());
            txtSearch.clear();
            refreshProductGrid();
        } else if (matches.isEmpty()) {
            setStatusMessage("No se encontró ningún artículo para: \"" + input + "\"");
        }
    }

    private void refreshProductGrid() {
        String query = txtSearch.getText();
        List<Product> products = inventoryService.search(query, selectedCategory);
        catalogGrid.setProducts(products, this::addProductToCart);
    }

    private void addProductToCart(Product product) {
        try {
            cartService.addProduct(product);
            setStatusMessage("Agregado al ticket: " + product.name());
        } catch (IllegalArgumentException ex) {
            setStatusMessage(ex.getMessage());
        }
    }

    private void setupKeyboardShortcuts() {
        rootPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F2) {
                txtSearch.requestFocus();
                txtSearch.selectAll();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && txtSearch.isFocused() && !txtSearch.getText().isEmpty()) {
                txtSearch.clear();
                event.consume();
            } else if (event.getCode() == KeyCode.F5) {
                if (!cartService.isEmpty()) {
                    checkoutPanel.handleCancelAction();
                    event.consume();
                }
            } else if (event.getCode() == KeyCode.F12) {
                if (!cartService.isEmpty()) {
                    checkoutPanel.handleChargeAction();
                    event.consume();
                }
            }
        });
    }

    private void setStatusMessage(String message) {
        lblStatusMessage.setText(message);
    }
}
