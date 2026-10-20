package com.paperstation.pos.service;

import com.paperstation.pos.model.CartItem;
import com.paperstation.pos.model.Order;
import com.paperstation.pos.model.OrderItem;
import com.paperstation.pos.model.Product;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Estado global y reactivo del carrito de venta.
 *
 * <h2>Modelo de cálculo</h2>
 * <pre>
 *   Subtotal  = Σ (precio × cantidad)
 *   Descuento = DiscountPolicy(subtotal), acotado a [0, subtotal]
 *   Base      = Subtotal − Descuento
 *   IVA       = Base × 16 %
 *   Total     = Base + IVA
 * </pre>
 * Todo se calcula con {@link BigDecimal} a escala 2 y {@link RoundingMode#HALF_UP}; la conversión
 * a {@code double} ocurre únicamente en la capa de exposición para la UI. Los valores exactos
 * están disponibles vía {@link #totalsProperty()} / {@link #getTotals()}.
 *
 * <h2>Reactividad</h2>
 * La lista usa un <em>extractor</em> sobre {@link CartItem#quantity()}, de modo que cualquier
 * cambio de cantidad emite un evento de actualización de la lista. Un único
 * {@link ObjectBinding} de {@link CartTotals} depende de la lista y de la política de descuento;
 * todas las propiedades numéricas derivan de él, garantizando que Subtotal, Descuento, IVA y Total
 * sean siempre coherentes entre sí.
 *
 * <h2>Hilos</h2>
 * Como cualquier objeto observable de JavaFX enlazado a la UI, esta clase <strong>no es
 * thread-safe</strong>: debe usarse desde el JavaFX Application Thread
 * (usar {@code Platform.runLater} desde tareas en segundo plano).
 */
public final class CartService {

    /** Tasa de IVA general en México. */
    public static final BigDecimal IVA_RATE = new BigDecimal("0.16");

    /** Límite de seguridad por línea para evitar capturas erróneas (p. ej. escáner repetido). */
    public static final int MAX_QUANTITY_PER_ITEM = 99_999;

    private static final int SCALE = Product.MONEY_SCALE;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final DateTimeFormatter FOLIO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    // ---------------------------------------------------------------- estado

    private final ObservableList<CartItem> items =
            FXCollections.observableArrayList(item -> new Observable[]{item.quantity()});
    private final ObservableList<CartItem> readOnlyItems =
            FXCollections.unmodifiableObservableList(items);

    private final ReadOnlyObjectWrapper<DiscountPolicy> discountPolicy =
            new ReadOnlyObjectWrapper<>(this, "discountPolicy", DiscountPolicy.none());

    // ---------------------------------------------------------------- derivados

    private final ReadOnlyObjectWrapper<CartTotals> totals = new ReadOnlyObjectWrapper<>(this, "totals");

    private final ReadOnlyDoubleWrapper subtotal = new ReadOnlyDoubleWrapper(this, "subtotal");
    private final ReadOnlyDoubleWrapper discount = new ReadOnlyDoubleWrapper(this, "discount");
    private final ReadOnlyDoubleWrapper tax = new ReadOnlyDoubleWrapper(this, "tax");
    private final ReadOnlyDoubleWrapper total = new ReadOnlyDoubleWrapper(this, "total");

    private final ReadOnlyIntegerWrapper itemCount = new ReadOnlyIntegerWrapper(this, "itemCount");
    private final ReadOnlyBooleanWrapper empty = new ReadOnlyBooleanWrapper(this, "empty");

    // ---------------------------------------------------------------- infraestructura

    private final Clock clock;
    private final Supplier<String> orderIdGenerator;

    /**
     * Protege la invariante {@code 1 <= cantidad <= MAX} incluso si alguien escribe directamente
     * en {@link CartItem#quantity()} sin pasar por el servicio: el valor inválido se revierte.
     */
    private final ChangeListener<Number> quantityGuard = (obs, oldVal, newVal) -> {
        int value = newVal.intValue();
        if (value < 1 || value > MAX_QUANTITY_PER_ITEM) {
            ((IntegerProperty) obs).set(oldVal.intValue());
        }
    };

    public CartService() {
        this(Clock.systemDefaultZone(), null);
    }

    /**
     * @param clock            reloj usado para fechar órdenes (inyectable para pruebas)
     * @param orderIdGenerator generador de folios; {@code null} para usar el predeterminado
     *                         ({@code PS-yyyyMMdd-HHmmss-NNNN})
     */
    public CartService(Clock clock, Supplier<String> orderIdGenerator) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.orderIdGenerator = orderIdGenerator != null ? orderIdGenerator : defaultOrderIdGenerator(clock);

        items.addListener(this::onItemsChanged);

        totals.bind(Bindings.createObjectBinding(this::computeTotals, items, discountPolicy));

        subtotal.bind(Bindings.createDoubleBinding(() -> totals.get().subtotal().doubleValue(), totals));
        discount.bind(Bindings.createDoubleBinding(() -> totals.get().discount().doubleValue(), totals));
        tax.bind(Bindings.createDoubleBinding(() -> totals.get().tax().doubleValue(), totals));
        total.bind(Bindings.createDoubleBinding(() -> totals.get().total().doubleValue(), totals));

        itemCount.bind(Bindings.createIntegerBinding(
                () -> items.stream().mapToInt(CartItem::getQuantity).sum(), items));
        empty.bind(Bindings.isEmpty(items));
    }

    // ================================================================ comandos

    /** Agrega una unidad del producto (o incrementa la línea existente). */
    public CartItem addProduct(Product product) {
        return addProduct(product, 1);
    }

    /**
     * Agrega {@code quantity} unidades del producto. Si ya existe una línea con el mismo
     * {@link Product#id()}, se incrementa su cantidad en lugar de duplicarla.
     *
     * @return la línea afectada
     * @throws IllegalArgumentException si la cantidad es menor a 1 o excede el máximo por línea
     */
    public CartItem addProduct(Product product, int quantity) {
        Objects.requireNonNull(product, "product");
        requireValidQuantity(quantity);

        Optional<CartItem> existing = findItem(product.id());
        if (existing.isPresent()) {
            CartItem item = existing.get();
            int newQuantity;
            try {
                newQuantity = Math.addExact(item.getQuantity(), quantity);
            } catch (ArithmeticException overflow) {
                throw new IllegalArgumentException("Cantidad fuera de rango", overflow);
            }
            requireValidQuantity(newQuantity);
            item.quantity().set(newQuantity);
            return item;
        }

        CartItem item = CartItem.of(product, quantity);
        items.add(item);
        return item;
    }

    /**
     * Elimina por completo la línea del producto.
     *
     * @return {@code true} si el producto estaba en el carrito
     */
    public boolean removeProduct(Product product) {
        Objects.requireNonNull(product, "product");
        return items.removeIf(item -> item.product().id().equals(product.id()));
    }

    /** Sobrecarga de conveniencia para la UI (filas de tabla). */
    public boolean removeProduct(CartItem item) {
        Objects.requireNonNull(item, "item");
        return removeProduct(item.product());
    }

    /**
     * Establece la cantidad absoluta de una línea. Una cantidad {@code <= 0} elimina la línea.
     *
     * @return {@code true} si el producto estaba en el carrito
     * @throws IllegalArgumentException si la cantidad excede el máximo por línea
     */
    public boolean updateQuantity(Product product, int newQuantity) {
        Objects.requireNonNull(product, "product");
        if (newQuantity <= 0) {
            return removeProduct(product);
        }
        requireValidQuantity(newQuantity);

        Optional<CartItem> existing = findItem(product.id());
        existing.ifPresent(item -> item.quantity().set(newQuantity));
        return existing.isPresent();
    }

    /** Sobrecarga de conveniencia para la UI (botones +/− de cada fila). */
    public boolean updateQuantity(CartItem item, int newQuantity) {
        Objects.requireNonNull(item, "item");
        return updateQuantity(item.product(), newQuantity);
    }

    /** Vacía el carrito y restablece el descuento. */
    public void clearCart() {
        items.clear();
        discountPolicy.set(DiscountPolicy.none());
    }

    /** Aplica una política de descuento; {@code null} equivale a {@link DiscountPolicy#none()}. */
    public void applyDiscount(DiscountPolicy policy) {
        discountPolicy.set(policy != null ? policy : DiscountPolicy.none());
    }

    public void clearDiscount() {
        applyDiscount(null);
    }

    /**
     * Confirma la venta: congela las líneas en un {@link Order} inmutable y vacía el carrito.
     * Obtén {@link #getTotals()} <em>antes</em> de llamar a este método si necesitas los
     * importes con descuento para el ticket.
     *
     * @throws IllegalStateException si el carrito está vacío
     */
    public Order checkout(String cashierName) {
        if (items.isEmpty()) {
            throw new IllegalStateException("No se puede cobrar un carrito vacío");
        }
        List<OrderItem> snapshot = items.stream().map(OrderItem::from).toList();
        Order order = new Order(orderIdGenerator.get(), cashierName, LocalDateTime.now(clock), snapshot);
        clearCart();
        return order;
    }

    // ================================================================ consultas

    /** Vista observable de <strong>solo lectura</strong> del carrito (enlazable a un TableView). */
    public ObservableList<CartItem> getItems() {
        return readOnlyItems;
    }

    public Optional<CartItem> findItem(String productId) {
        Objects.requireNonNull(productId, "productId");
        return items.stream()
                .filter(item -> item.product().id().equals(productId))
                .findFirst();
    }

    /** Totales exactos actuales. */
    public CartTotals getTotals() {
        return totals.get();
    }

    public ReadOnlyObjectProperty<CartTotals> totalsProperty() {
        return totals.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<DiscountPolicy> discountPolicyProperty() {
        return discountPolicy.getReadOnlyProperty();
    }

    public ReadOnlyDoubleProperty subtotalProperty() {
        return subtotal.getReadOnlyProperty();
    }

    public ReadOnlyDoubleProperty discountProperty() {
        return discount.getReadOnlyProperty();
    }

    public ReadOnlyDoubleProperty taxProperty() {
        return tax.getReadOnlyProperty();
    }

    public ReadOnlyDoubleProperty totalProperty() {
        return total.getReadOnlyProperty();
    }

    /** Total de unidades (suma de cantidades), no de líneas. */
    public ReadOnlyIntegerProperty itemCountProperty() {
        return itemCount.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty emptyProperty() {
        return empty.getReadOnlyProperty();
    }

    public double getSubtotal() { return subtotal.get(); }
    public double getDiscount() { return discount.get(); }
    public double getTax() { return tax.get(); }
    public double getTotal() { return total.get(); }
    public int getItemCount() { return itemCount.get(); }
    public boolean isEmpty() { return empty.get(); }

    // ================================================================ internos

    private CartTotals computeTotals() {
        BigDecimal sub = items.stream()
                .filter(item -> item.getQuantity() >= 1) // ignora un valor inválido transitorio antes de que quantityGuard lo revierta
                .map(CartItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, ROUNDING);

        BigDecimal disc = sanitizeDiscount(sub);
        BigDecimal base = sub.subtract(disc);
        BigDecimal iva = base.multiply(IVA_RATE).setScale(SCALE, ROUNDING);
        BigDecimal tot = base.add(iva).setScale(SCALE, ROUNDING);

        return new CartTotals(sub, disc, iva, tot);
    }

    /** Acota el descuento de la política a {@code [0, subtotal]} y lo normaliza a escala 2. */
    private BigDecimal sanitizeDiscount(BigDecimal sub) {
        DiscountPolicy policy = discountPolicy.get();
        BigDecimal raw = policy == null ? null : policy.discountFor(sub, readOnlyItems);
        if (raw == null || raw.signum() <= 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return raw.min(sub).setScale(SCALE, ROUNDING);
    }

    private void onItemsChanged(ListChangeListener.Change<? extends CartItem> change) {
        while (change.next()) {
            if (change.wasRemoved()) {
                change.getRemoved().forEach(item -> item.quantity().removeListener(quantityGuard));
            }
            if (change.wasAdded()) {
                change.getAddedSubList().forEach(item -> item.quantity().addListener(quantityGuard));
            }
        }
    }

    private static void requireValidQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("La cantidad debe ser al menos 1: " + quantity);
        }
        if (quantity > MAX_QUANTITY_PER_ITEM) {
            throw new IllegalArgumentException(
                    "La cantidad excede el máximo permitido (" + MAX_QUANTITY_PER_ITEM + "): " + quantity);
        }
    }

    private static Supplier<String> defaultOrderIdGenerator(Clock clock) {
        AtomicLong sequence = new AtomicLong();
        return () -> "PS-%s-%04d".formatted(
                LocalDateTime.now(clock).format(FOLIO_FORMAT), sequence.incrementAndGet());
    }
}
