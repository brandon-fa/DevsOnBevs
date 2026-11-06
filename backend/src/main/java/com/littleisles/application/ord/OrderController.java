package com.littleisles.application.ord;

import com.littleisles.domain.ord.CourierPin;
import com.littleisles.domain.ord.CourierStatus;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.OrderLine;
import com.littleisles.domain.ord.Product;
import com.littleisles.domain.reg.AccountCredentials;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.CustomerAccount;
import com.littleisles.domain.reg.GroceriesAccount;
import com.littleisles.domain.reg.Role;
import com.littleisles.infrastructure.db.OrderStore;
import com.littleisles.technicalservices.reg.AccountRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OrderController {
    private final CityCatalog cityCatalog;
    private final AccountRepository accountRepository;
    private final OrderStore orderStore;
    // Keep one domain customer per database id, shared by all of that customer's sessions.
    private final Map<Long, CustomerAccount> customers = new HashMap<>();
    private final Map<Long, List<Order>> savedOrdersByCustomer = new HashMap<>();
    private final Map<Long, GroceriesAccount> groceriesAccounts = new HashMap<>();
    private final List<Order> deliveries = new ArrayList<>();

    public OrderController(CityCatalog cityCatalog, AccountRepository accountRepository) throws SQLException {
        this(cityCatalog, accountRepository, new OrderStore());
    }

    public OrderController(CityCatalog cityCatalog, AccountRepository accountRepository, OrderStore orderStore) throws SQLException {
        this.cityCatalog = cityCatalog;
        this.accountRepository = accountRepository;
        this.orderStore = orderStore;
        restoreCourierStatuses();
        loadSavedOrders();
    }

    public synchronized Order placeOrder(AuthenticatedAccount account, String storeId, String destination,
                                         Map<String, Integer> products, boolean saveAsDefault) throws SQLException {
        CustomerAccount customer = customerFor(account);
        if (storeId == null || storeId.isBlank()) {
            throw new IllegalArgumentException("Choose a grocery store.");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Enter a delivery address.");
        }
        if (products == null || products.isEmpty()) {
            throw new IllegalArgumentException("Add at least one product to your order.");
        }
        GroceryStore store = findStore(storeId);
        if (!store.isOpen()) {
            throw new IllegalStateException("This grocery store is closed.");
        }

        List<OrderLine> validatedLines = new ArrayList<>();
        for (String productId : products.keySet()) {
            Integer quantity = products.get(productId);
            if (productId == null || productId.isBlank() || quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("Each product needs an id and a positive whole-number quantity.");
            }
            Product product = findProduct(store, productId);
            if (product.isSoldOut()) {
                throw new IllegalStateException(product.getName() + " is sold out.");
            }
            validatedLines.add(new OrderLine(product, quantity));
        }

        Order order = new Order(store, destination.trim());
        for (OrderLine line : validatedLines) {
            order.addOrderLine(line.getProduct(), line.getQuantity());
        }
        order.placeOrder();
        try {
            int savedId = orderStore.savePlacedOrder(account.id(), store.getId(), order.getDestination(), order);
            order.assignPersistedId(savedId);
            if (saveAsDefault) {
                accountRepository.saveDefaultAddress(account.id(), order.getDestination());
            }
        } catch (SQLException exception) {
            store.getPendingOrders().remove(order);
            throw exception;
        }
        customer.addOrder(order);
        return order;
    }

    public synchronized List<Order> customerOrders(AuthenticatedAccount account) throws SQLException {
        CustomerAccount customer = customerFor(account);
        return List.copyOf(customer.getOrders());
    }

    public synchronized void associateGroceriesAccount(String email, String storeId) throws SQLException {
        AccountCredentials stored = accountRepository.findByEmail(email);
        if (stored == null || !Role.GROCERIES.label().equals(stored.role())) {
            throw new IllegalArgumentException("Store associations require an existing groceries account: " + email);
        }
        GroceryStore store = findStore(storeId);
        GroceriesAccount groceries = new GroceriesAccount(stored.fullName(), stored.email(), stored.passwordHash(), stored.phone());
        groceries.setStore(store);
        groceriesAccounts.put(stored.id(), groceries);
    }

    public synchronized GroceryStore storeOrders(AuthenticatedAccount account) {
        return storeFor(account);
    }

    public synchronized GroceryStore acceptPendingOrder(AuthenticatedAccount account, int orderId) throws SQLException {
        GroceryStore store = storeFor(account);
        Order order = findPendingOrder(store, orderId);
        store.acceptPendingOrder(order);
        orderStore.updateStatus(order.getOrderId(), order.getStatus());
        return store;
    }

    public synchronized GroceryStore readyNextOrder(AuthenticatedAccount account, int orderId) throws SQLException {
        GroceryStore store = storeFor(account);
        if (orderId <= 0) {
            throw new IllegalArgumentException("Choose a valid order id.");
        }
        store.readyNextOrder(orderId);
        orderStore.updateStatus(orderId, "ready");
        for (Order order : store.getReadyOrders()) {
            if (order.getOrderId() == orderId) {
                offerToNearestIdleCourier(store, order);
                break;
            }
        }
        return store;
    }

    public synchronized List<Order> courierOffers(AuthenticatedAccount account) throws SQLException {
        long courierUserId = requireCourier(account);
        List<Order> offers = new ArrayList<>();
        for (GroceryStore store : cityCatalog.snapshot().groceryStores()) {
            for (Order order : store.getReadyOrders()) {
                if (Long.valueOf(courierUserId).equals(order.getOfferedCourierUserId()) && !"delivered".equals(order.getStatus())) {
                    offers.add(order);
                }
            }
        }
        for (Order order : deliveries) {
            if (Long.valueOf(courierUserId).equals(order.getOfferedCourierUserId()) && !"delivered".equals(order.getStatus()) && !offers.contains(order)) {
                offers.add(order);
            }
        }
        return List.copyOf(offers);
    }

    public synchronized void acceptCourierOffer(AuthenticatedAccount account, int orderId) throws SQLException {
        long courierUserId = requireCourier(account);
        if (orderId <= 0) {
            throw new IllegalArgumentException("Choose a valid order id.");
        }
        Order offered = null;
        for (Order order : courierOffers(account)) {
            if (order.getOrderId() == orderId) {
                offered = order;
                break;
            }
        }
        if (offered == null) {
            throw new IllegalStateException("This delivery is not offered to you.");
        }
        String pin = accountRepository.courierPinForUser(courierUserId);
        if (pin == null || pin.isBlank()) {
            throw new IllegalStateException("Your courier account is not on the city map.");
        }
        offered.markCourierAccepted();
        orderStore.updateStatus(offered.getOrderId(), "accepted");
        rememberCourierStatus(courierUserId, pin, CourierStatus.ASSIGNED);
        cityCatalog.clearOpenOffer(pin);
    }

    public synchronized void pickupCourierOrder(AuthenticatedAccount account, int orderId) throws SQLException {
        Order order = deliveryFor(account, orderId);
        if (!order.isCourierAccepted() || !"ready".equals(order.getStatus())) {
            throw new IllegalStateException("Accept the delivery before picking it up.");
        }
        order.pickupOrder();
        orderStore.updateStatus(order.getOrderId(), order.getStatus());
        rememberCourierStatus(account.id(), accountRepository.courierPinForUser(account.id()), CourierStatus.CARRYING);
        if (!deliveries.contains(order)) {
            deliveries.add(order);
        }
    }

    public synchronized void deliverCourierOrder(AuthenticatedAccount account, int orderId) throws SQLException {
        Order order = deliveryFor(account, orderId);
        if (!"picked_up".equals(order.getStatus())) {
            throw new IllegalStateException("Pick up the order before marking it delivered.");
        }
        order.deliverOrder();
        orderStore.updateStatus(order.getOrderId(), order.getStatus());
        deliveries.remove(order);
        rememberCourierStatus(account.id(), accountRepository.courierPinForUser(account.id()), CourierStatus.IDLE);
    }

    private Order deliveryFor(AuthenticatedAccount account, int orderId) throws SQLException {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Choose a valid order id.");
        }
        for (Order order : courierOffers(account)) {
            if (order.getOrderId() == orderId) {
                return order;
            }
        }
        throw new IllegalStateException("This delivery is not assigned to you.");
    }

    private void rememberCourierStatus(long courierUserId, String pin, CourierStatus status) throws SQLException {
        accountRepository.updateCourierStatus(courierUserId, status.label());
        if (pin != null && !pin.isBlank()) {
            cityCatalog.setCourierStatus(pin, status);
        }
    }

    private void offerToNearestIdleCourier(GroceryStore store, Order order) throws SQLException {
        CourierPin nearest = null;
        long nearestUserId = 0;
        double best = Double.MAX_VALUE;
        for (CourierPin pin : cityCatalog.snapshot().couriers()) {
            if (pin.status() != CourierStatus.IDLE || cityCatalog.hasOpenOffer(pin.id())) {
                continue;
            }
            Long userId = accountRepository.courierUserId(pin.id());
            if (userId == null) {
                continue;
            }
            double dLat = store.getLat() - pin.lat();
            double dLon = store.getLon() - pin.lon();
            double distance = dLat * dLat + dLon * dLon;
            if (distance < best) {
                best = distance;
                nearest = pin;
                nearestUserId = userId;
            }
        }
        if (nearest == null) {
            return;
        }
        order.setOfferedCourierUserId(nearestUserId);
        orderStore.assignCourier(order.getOrderId(), nearestUserId);
        cityCatalog.markOpenOffer(nearest.id());
    }

    private long requireCourier(AuthenticatedAccount account) {
        if (account == null || account.role() != Role.COURIER) {
            throw new SecurityException("Only couriers can accept a delivery.");
        }
        return account.id();
    }

    public synchronized GroceryStore rejectPendingOrder(AuthenticatedAccount account, int orderId, String reason) throws SQLException {
        GroceryStore store = storeFor(account);
        Order order = findPendingOrder(store, orderId);
        store.rejectPendingOrder(order, reason);
        orderStore.updateStatus(order.getOrderId(), order.getStatus());
        return store;
    }

    private GroceryStore findStore(String storeId) {
        for (GroceryStore store : cityCatalog.snapshot().groceryStores()) {
            if (store.getId().equals(storeId)) {
                return store;
            }
        }
        throw new IllegalArgumentException("The grocery store does not exist: " + storeId);
    }

    private Product findProduct(GroceryStore store, String productId) {
        for (Product product : store.getProducts()) {
            if (product.getId().equals(productId)) {
                return product;
            }
        }
        throw new IllegalArgumentException("A product does not belong to this grocery store.");
    }

    private Order findPendingOrder(GroceryStore store, int orderId) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Choose a valid order id.");
        }
        for (Order order : store.getPendingOrders()) {
            if (order.getOrderId() == orderId) {
                return order;
            }
        }
        throw new IllegalStateException("This order is no longer pending at your store.");
    }

    private GroceryStore storeFor(AuthenticatedAccount account) {
        if (account == null || account.role() != Role.GROCERIES) {
            throw new SecurityException("Only groceries accounts can manage store orders.");
        }
        GroceriesAccount groceries = groceriesAccounts.get(account.id());
        if (groceries == null) {
            throw new SecurityException("Your groceries account has no associated grocery store.");
        }
        return groceries.getStore();
    }

    private CustomerAccount customerFor(AuthenticatedAccount account) throws SQLException {
        if (account == null || account.role() != Role.CUSTOMER) {
            throw new SecurityException("Only customers can place or view their orders.");
        }
        CustomerAccount customer = customers.get(account.id());
        if (customer == null) {
            AccountCredentials stored = accountRepository.findByEmail(account.email());
            if (stored == null || stored.id() != account.id() || !Role.CUSTOMER.label().equals(stored.role())) {
                throw new SecurityException("The customer account is unavailable.");
            }
            customer = new CustomerAccount(stored.fullName(), stored.email(), stored.passwordHash(), stored.phone());
            List<Order> saved = savedOrdersByCustomer.remove(account.id());
            if (saved != null) {
                for (Order order : saved) {
                    customer.addOrder(order);
                }
            }
            customers.put(account.id(), customer);
        }
        return customer;
    }

    private void restoreCourierStatuses() throws SQLException {
        List<String> pins = new ArrayList<>();
        for (CourierPin pin : cityCatalog.snapshot().couriers()) {
            pins.add(pin.id());
        }
        for (String pin : pins) {
            String stored = accountRepository.courierStatusForPin(pin);
            if (stored == null || stored.isBlank()) continue;
            cityCatalog.setCourierStatus(pin, CourierStatus.fromLabel(stored));
        }
    }

    private void loadSavedOrders() throws SQLException {
        for (OrderStore.SavedOrder saved : orderStore.loadAll()) {
            GroceryStore store;
            try {
                store = findStore(saved.storeId());
            } catch (IllegalArgumentException exception) {
                continue;
            }
            Order order = new Order(store, saved.destination());
            order.restoreSaved(saved.id(), saved.status(), saved.lines());
            if ("placed".equals(saved.status())) {
                store.addPendingOrder(order);
            } else if ("preparing".equals(saved.status())) {
                store.addOrderToQueue(order);
            } else if ("ready".equals(saved.status()) || "accepted".equals(saved.status())) {
                store.restoreReadyOrder(order);
                if (saved.courierUserId() != null) {
                    order.setOfferedCourierUserId(saved.courierUserId());
                    String pin = accountRepository.courierPinForUser(saved.courierUserId());
                    boolean accepted = "accepted".equals(saved.status()) || (pin != null && cityCatalog.snapshot().couriers().stream()
                            .anyMatch(courier -> courier.id().equals(pin) && courier.status() != CourierStatus.IDLE));
                    if (accepted) {
                        order.markCourierAccepted();
                    } else if (pin != null && !pin.isBlank()) {
                        cityCatalog.markOpenOffer(pin);
                    }
                }
            } else if ("picked_up".equals(saved.status()) && saved.courierUserId() != null) {
                order.setOfferedCourierUserId(saved.courierUserId());
                order.markCourierAccepted();
                deliveries.add(order);
            }
            savedOrdersByCustomer.computeIfAbsent(saved.customerId(), id -> new ArrayList<>()).add(order);
        }
    }
}
