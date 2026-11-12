package com.littleisles.application.ord;

import com.littleisles.domain.ord.AddressLocator;
import com.littleisles.domain.ord.CitySnapshot;
import com.littleisles.domain.ord.CourierPin;
import com.littleisles.domain.ord.CourierRosterEntry;
import com.littleisles.domain.ord.CourierStatus;
import com.littleisles.domain.ord.CustomerPin;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.OrderLine;
import com.littleisles.domain.ord.Product;
import com.littleisles.domain.ord.StoreStatus;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OrderController {
    private final CityCatalog cityCatalog;
    private final AccountRepository accountRepository;
    private final OrderStore orderStore;
    private final CourierAvailability courierAvailability = new CourierAvailability();
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
        courierAvailability.addObserver(ignoredCourierUserId -> offerUnassignedReadyOrders());
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
        // Resolve coordinates up front so the map pin matches the typed street address.
        AddressLocator.locate(destination.trim(), cityCatalog.snapshot().bounds());
        GroceryStore store = findStore(storeId);
        if (store.isPaused()) {
            throw new IllegalStateException("This grocery store is paused and is not taking new orders.");
        }
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
        order.setCustomerUserId(account.id());
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
        offerUnassignedReadyOrders();
        return store;
    }

    public synchronized List<Order> courierOffers(AuthenticatedAccount account) throws SQLException {
        long courierUserId = requireCourier(account);
        offerUnassignedReadyOrders();
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

    public synchronized CourierStatus courierStatus(AuthenticatedAccount account) throws SQLException {
        long courierUserId = requireCourier(account);
        String pin = accountRepository.courierPinForUser(courierUserId);
        if (pin == null || pin.isBlank()) {
            throw new IllegalStateException("Your courier account is not on the city map.");
        }
        return cityCatalog.snapshot().couriers().stream()
                .filter(courier -> courier.id().equals(pin))
                .map(CourierPin::status)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Your courier account is not on the city map."));
    }

    public synchronized CourierStatus setCourierOnline(AuthenticatedAccount account, boolean online) throws SQLException {
        long courierUserId = requireCourier(account);
        String pin = accountRepository.courierPinForUser(courierUserId);
        if (pin == null || pin.isBlank()) {
            throw new IllegalStateException("Your courier account is not on the city map.");
        }
        CourierStatus status = online ? CourierStatus.IDLE : CourierStatus.OFFLINE;
        rememberCourierStatus(courierUserId, pin, status);
        if (online) {
            courierAvailability.notifyCourierAvailable(courierUserId);
        }
        return status;
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
        ensureNoOtherActiveOrder(courierUserId, offered.getOrderId());
        String pin = accountRepository.courierPinForUser(courierUserId);
        if (pin == null || pin.isBlank()) {
            throw new IllegalStateException("Your courier account is not on the city map.");
        }
        offered.markCourierAccepted();
        orderStore.updateStatus(offered.getOrderId(), "accepted");
        rememberCourierStatus(courierUserId, pin, CourierStatus.ASSIGNED);
        cityCatalog.clearOpenOffer(pin);
        offerUnassignedReadyOrders();
    }

    public synchronized void pickupCourierOrder(AuthenticatedAccount account, int orderId) throws SQLException {
        Order order = deliveryFor(account, orderId);
        if (!order.isCourierAccepted() || !"ready".equals(order.getStatus())) {
            throw new IllegalStateException("Accept the delivery before picking it up.");
        }
        ensureNoOtherActiveOrder(account.id(), order.getOrderId());
        order.pickupOrder();
        orderStore.updateStatus(order.getOrderId(), order.getStatus());
        String pin = accountRepository.courierPinForUser(account.id());
        rememberCourierStatus(account.id(), pin, CourierStatus.CARRYING);
        rememberCourierLocation(account.id(), pin, order.getStore().getLat(), order.getStore().getLon());
        if (!deliveries.contains(order)) {
            deliveries.add(order);
        }
    }

    public synchronized List<CourierRosterEntry> operatorCouriers(AuthenticatedAccount account) throws SQLException {
        requireOperator(account);
        return accountRepository.listCouriers();
    }

    public synchronized List<Order> operatorCourierOrders(AuthenticatedAccount account) throws SQLException {
        requireOperator(account);
        List<Order> orders = new ArrayList<>();
        for (GroceryStore store : cityCatalog.snapshot().groceryStores()) {
            for (Order order : store.getReadyOrders()) {
                if (order.getOfferedCourierUserId() != null && !"delivered".equals(order.getStatus())) {
                    orders.add(order);
                }
            }
        }
        for (Order order : deliveries) {
            if (order.getOfferedCourierUserId() != null
                    && !"delivered".equals(order.getStatus())
                    && !orders.contains(order)) {
                orders.add(order);
            }
        }
        return List.copyOf(orders);
    }

    public synchronized void reassignAcceptedOrder(AuthenticatedAccount account, int orderId, long targetCourierUserId) throws SQLException {
        requireOperator(account);
        if (orderId <= 0) {
            throw new IllegalArgumentException("Choose a valid order id.");
        }
        if (targetCourierUserId <= 0) {
            throw new IllegalArgumentException("Choose a courier.");
        }
        Order order = findAssignedOrder(orderId);
        if (order == null) {
            throw new IllegalArgumentException("That delivery is not available to reassign.");
        }
        if (!order.isCourierAccepted() || !"ready".equals(order.getStatus())) {
            throw new IllegalStateException("Only an accepted delivery can be reassigned.");
        }
        String targetPin = accountRepository.courierPinForUser(targetCourierUserId);
        if (targetPin == null || targetPin.isBlank()) {
            throw new IllegalArgumentException("Choose a courier on the city map.");
        }
        Long currentCourier = order.getOfferedCourierUserId();
        if (currentCourier != null && currentCourier == targetCourierUserId) {
            throw new IllegalArgumentException("This order is already assigned to that courier.");
        }
        ensureNoOtherActiveOrder(targetCourierUserId, order.getOrderId());
        order.setOfferedCourierUserId(targetCourierUserId);
        orderStore.assignCourier(order.getOrderId(), targetCourierUserId);
        rememberCourierStatus(targetCourierUserId, targetPin, statusAfterAssignment(targetCourierUserId));
        cityCatalog.clearOpenOffer(targetPin);
        if (currentCourier != null) {
            releaseCourierIfFree(currentCourier);
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
        String pin = accountRepository.courierPinForUser(account.id());
        double[] delivery = AddressLocator.locate(order.getDestination(), cityCatalog.snapshot().bounds());
        rememberCourierLocation(account.id(), pin, delivery[0], delivery[1]);
        rememberCourierStatus(account.id(), pin, CourierStatus.IDLE);
        if (pin != null && !pin.isBlank()) {
            cityCatalog.clearOpenOffer(pin);
        }
        courierAvailability.notifyCourierAvailable(account.id());
    }

    public synchronized String cityJson(AuthenticatedAccount account) throws SQLException {
        if (account == null) {
            throw new SecurityException("Log in to continue.");
        }
        List<CustomerPin> pins = liveCustomerPins(account);
        CitySnapshot base = cityCatalog.snapshot();
        List<CourierPin> couriers = mapCouriersAtPickupStops(base.couriers());
        if (account.role() == Role.CUSTOMER) {
            List<CourierPin> visible = new ArrayList<>();
            for (ActiveOrder active : activeOrders()) {
                if (active.customerId() == null || active.customerId() != account.id()) continue;
                if (!active.courierAccepted() || active.courierUserId() == null) continue;
                if (!"picked_up".equals(active.status()) && !"ready".equals(active.status()) && !"accepted".equals(active.status())) {
                    continue;
                }
                String pinId = accountRepository.courierPinForUser(active.courierUserId());
                if (pinId == null) continue;
                for (CourierPin courier : couriers) {
                    if (courier.id().equals(pinId) && !visible.contains(courier)) {
                        visible.add(courier);
                    }
                }
            }
            couriers = visible;
        }
        return new CitySnapshot(base.bounds(), base.groceryStores(), couriers, pins).toJson();
    }

    /** Couriers with a picked-up delivery are shown at that order's grocery store. */
    private List<CourierPin> mapCouriersAtPickupStops(List<CourierPin> couriers) throws SQLException {
        List<CourierPin> mapped = new ArrayList<>(couriers.size());
        for (CourierPin courier : couriers) {
            GroceryStore pickupStore = pickupStoreForCourier(courier);
            if (pickupStore != null) {
                mapped.add(new CourierPin(
                        courier.id(),
                        courier.name(),
                        pickupStore.getLat(),
                        pickupStore.getLon(),
                        CourierStatus.CARRYING));
            } else {
                mapped.add(courier);
            }
        }
        return mapped;
    }

    private GroceryStore pickupStoreForCourier(CourierPin courier) throws SQLException {
        Long userId = accountRepository.courierUserId(courier.id());
        if (userId == null) {
            return null;
        }
        for (Order order : deliveries) {
            if (userId.equals(order.getOfferedCourierUserId()) && "picked_up".equals(order.getStatus())) {
                return order.getStore();
            }
        }
        return null;
    }

    private void rememberCourierLocation(long courierUserId, String pin, double lat, double lon) throws SQLException {
        accountRepository.updateCourierLocation(courierUserId, lat, lon);
        if (pin != null && !pin.isBlank()) {
            cityCatalog.setCourierLocation(pin, lat, lon);
        }
    }

    private List<CustomerPin> liveCustomerPins(AuthenticatedAccount account) throws SQLException {
        CityBoundsHolder bounds = new CityBoundsHolder(cityCatalog.snapshot().bounds());
        Map<String, PinBuilder> builders = new LinkedHashMap<>();

        if (account.role() == Role.CUSTOMER || account.role() == Role.OPERATOR) {
            for (AccountCredentials customer : accountRepository.listCustomerAccounts()) {
                if (account.role() == Role.CUSTOMER && customer.id() != account.id()) continue;
                String defaultAddress = customer.defaultAddress();
                if (defaultAddress == null || defaultAddress.isBlank()) continue;
                putPin(builders, customer.id(), customer.fullName(), defaultAddress, bounds, null);
            }
        }

        for (ActiveOrder active : activeOrders()) {
            if (account.role() == Role.CUSTOMER && (active.customerId() == null || active.customerId() != account.id())) {
                continue;
            }
            if (account.role() == Role.GROCERIES) {
                GroceryStore store = storeFor(account);
                if (!store.getId().equals(active.storeId())) continue;
            }
            if (account.role() == Role.COURIER) {
                if (!active.courierAccepted() || !Long.valueOf(account.id()).equals(active.courierUserId())) {
                    continue;
                }
            }
            AccountCredentials customer = findCustomerCredentials(active.customerId());
            String name = customer == null ? "Customer" : customer.fullName();
            long customerId = active.customerId() == null ? 0L : active.customerId();
            putPin(builders, customerId, name, active.destination(), bounds,
                    new CustomerPin.CustomerOrderRef(active.orderId(), active.status(), active.storeName()));
        }

        List<CustomerPin> pins = new ArrayList<>();
        for (PinBuilder builder : builders.values()) {
            CustomerPin pin = builder.build(bounds);
            if (pin != null) {
                pins.add(pin);
            }
        }
        return pins;
    }

    private AccountCredentials findCustomerCredentials(Long customerId) throws SQLException {
        if (customerId == null) return null;
        for (AccountCredentials customer : accountRepository.listCustomerAccounts()) {
            if (customer.id() == customerId) return customer;
        }
        return null;
    }

    private List<ActiveOrder> activeOrders() {
        List<ActiveOrder> active = new ArrayList<>();
        for (Map.Entry<Long, CustomerAccount> entry : customers.entrySet()) {
            for (Order order : entry.getValue().getOrders()) {
                if (!Order.isCurrent(order.getStatus())) continue;
                active.add(new ActiveOrder(
                        order.getOrderId(),
                        entry.getKey(),
                        order.getStore().getId(),
                        order.getStore().getName(),
                        order.getDestination(),
                        order.getStatus(),
                        order.getOfferedCourierUserId(),
                        order.isCourierAccepted()));
            }
        }
        for (Map.Entry<Long, List<Order>> entry : savedOrdersByCustomer.entrySet()) {
            if (customers.containsKey(entry.getKey())) continue;
            for (Order order : entry.getValue()) {
                if (!Order.isCurrent(order.getStatus())) continue;
                active.add(new ActiveOrder(
                        order.getOrderId(),
                        entry.getKey(),
                        order.getStore().getId(),
                        order.getStore().getName(),
                        order.getDestination(),
                        order.getStatus(),
                        order.getOfferedCourierUserId(),
                        order.isCourierAccepted()));
            }
        }
        for (Order order : deliveries) {
            if (!Order.isCurrent(order.getStatus())) continue;
            boolean already = false;
            for (ActiveOrder existing : active) {
                if (existing.orderId() == order.getOrderId()) {
                    already = true;
                    break;
                }
            }
            if (already) continue;
            active.add(new ActiveOrder(
                    order.getOrderId(),
                    order.getCustomerUserId(),
                    order.getStore().getId(),
                    order.getStore().getName(),
                    order.getDestination(),
                    order.getStatus(),
                    order.getOfferedCourierUserId(),
                    order.isCourierAccepted()));
        }
        return active;
    }

    private static void putPin(Map<String, PinBuilder> builders, long customerId, String name, String address,
                               CityBoundsHolder bounds, CustomerPin.CustomerOrderRef order) {
        String key = customerId + "|" + AddressLocator.normalize(address);
        PinBuilder builder = builders.get(key);
        if (builder == null) {
            builder = new PinBuilder(customerId, name, address);
            builders.put(key, builder);
        }
        if (order != null) {
            builder.addOrder(order);
        }
    }

    private record ActiveOrder(int orderId, Long customerId, String storeId, String storeName, String destination,
                               String status, Long courierUserId, boolean courierAccepted) {
    }

    private record CityBoundsHolder(com.littleisles.domain.ord.CityBounds bounds) {
    }

    private static final class PinBuilder {
        private final long customerId;
        private final String name;
        private final String address;
        private final List<CustomerPin.CustomerOrderRef> orders = new ArrayList<>();

        private PinBuilder(long customerId, String name, String address) {
            this.customerId = customerId;
            this.name = name;
            this.address = address;
        }

        private void addOrder(CustomerPin.CustomerOrderRef order) {
            orders.add(order);
        }

        private CustomerPin build(CityBoundsHolder bounds) {
            double[] point = AddressLocator.tryLocate(address, bounds.bounds());
            if (point == null) {
                return null;
            }
            return new CustomerPin(
                    "customer-" + customerId + "-" + Integer.toHexString(AddressLocator.normalize(address).hashCode()),
                    name,
                    point[0],
                    point[1],
                    address,
                    orders);
        }
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
        if (status == CourierStatus.OFFLINE && hasPickedUpOrder(courierUserId)) {
            throw new IllegalStateException("Deliver your current order before going offline.");
        }
        accountRepository.updateCourierStatus(courierUserId, status.label());
        if (pin != null && !pin.isBlank()) {
            cityCatalog.setCourierStatus(pin, status);
        }
    }

    private void ensureNoOtherActiveOrder(long courierUserId, int exceptOrderId) {
        for (GroceryStore store : cityCatalog.snapshot().groceryStores()) {
            for (Order order : store.getReadyOrders()) {
                if (order.getOrderId() != exceptOrderId
                        && Long.valueOf(courierUserId).equals(order.getOfferedCourierUserId())
                        && !"delivered".equals(order.getStatus())) {
                    throw new IllegalStateException("A courier can only have one active order at a time.");
                }
            }
        }
        for (Order order : deliveries) {
            if (order.getOrderId() != exceptOrderId
                    && Long.valueOf(courierUserId).equals(order.getOfferedCourierUserId())
                    && !"delivered".equals(order.getStatus())) {
                throw new IllegalStateException("A courier can only have one active order at a time.");
            }
        }
    }

    private boolean hasPickedUpOrder(long courierUserId) {
        for (Order order : deliveries) {
            if (Long.valueOf(courierUserId).equals(order.getOfferedCourierUserId())
                    && "picked_up".equals(order.getStatus())) {
                return true;
            }
        }
        return false;
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
            if (distance < best || (Double.compare(distance, best) == 0
                    && (nearest == null || userId < nearestUserId))) {
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

    private Order findAssignedOrder(int orderId) {
        for (GroceryStore store : cityCatalog.snapshot().groceryStores()) {
            for (Order order : store.getReadyOrders()) {
                if (order.getOrderId() == orderId) {
                    return order;
                }
            }
        }
        for (Order order : deliveries) {
            if (order.getOrderId() == orderId) {
                return order;
            }
        }
        return null;
    }

    private CourierStatus statusAfterAssignment(long courierUserId) {
        for (Order order : deliveries) {
            if (Long.valueOf(courierUserId).equals(order.getOfferedCourierUserId()) && "picked_up".equals(order.getStatus())) {
                return CourierStatus.CARRYING;
            }
        }
        return CourierStatus.ASSIGNED;
    }

    private void releaseCourierIfFree(long courierUserId) throws SQLException {
        for (GroceryStore store : cityCatalog.snapshot().groceryStores()) {
            for (Order order : store.getReadyOrders()) {
                if (Long.valueOf(courierUserId).equals(order.getOfferedCourierUserId()) && !"delivered".equals(order.getStatus())) {
                    return;
                }
            }
        }
        for (Order order : deliveries) {
            if (Long.valueOf(courierUserId).equals(order.getOfferedCourierUserId()) && !"delivered".equals(order.getStatus())) {
                return;
            }
        }
        String pin = accountRepository.courierPinForUser(courierUserId);
        rememberCourierStatus(courierUserId, pin, CourierStatus.IDLE);
        if (pin != null && !pin.isBlank()) {
            cityCatalog.clearOpenOffer(pin);
        }
        courierAvailability.notifyCourierAvailable(courierUserId);
    }

    private void offerUnassignedReadyOrders() throws SQLException {
        reconcileCourierAvailability();
        for (GroceryStore store : cityCatalog.snapshot().groceryStores()) {
            for (Order order : store.getReadyOrders()) {
                if (order.getOfferedCourierUserId() != null || order.isCourierAccepted()) {
                    continue;
                }
                offerToNearestIdleCourier(store, order);
            }
        }
    }

    private void reconcileCourierAvailability() throws SQLException {
        for (CourierPin pin : List.copyOf(cityCatalog.snapshot().couriers())) {
            Long userId = accountRepository.courierUserId(pin.id());
            boolean hasPendingOffer = false;
            boolean hasActiveWork = false;
            if (userId != null) {
                for (GroceryStore store : cityCatalog.snapshot().groceryStores()) {
                    for (Order order : store.getReadyOrders()) {
                        if (!userId.equals(order.getOfferedCourierUserId())) {
                            continue;
                        }
                        hasActiveWork = true;
                        if (!order.isCourierAccepted()) {
                            hasPendingOffer = true;
                        }
                    }
                }
                for (Order order : deliveries) {
                    if (userId.equals(order.getOfferedCourierUserId())) {
                        hasActiveWork = true;
                    }
                }
            }
            if (cityCatalog.hasOpenOffer(pin.id()) && !hasPendingOffer) {
                cityCatalog.clearOpenOffer(pin.id());
            }
            if (userId != null
                    && pin.status() != CourierStatus.IDLE
                    && pin.status() != CourierStatus.OFFLINE
                    && !hasActiveWork) {
                rememberCourierStatus(userId, pin.id(), CourierStatus.IDLE);
                cityCatalog.clearOpenOffer(pin.id());
            }
        }
    }

    private void requireOperator(AuthenticatedAccount account) {
        if (account == null || account.role() != Role.OPERATOR) {
            throw new SecurityException("Only the operator can view couriers and reassign deliveries.");
        }
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
        // Drop the persisted row instead of leaving a cancelled status behind.
        orderStore.delete(order.getOrderId());
        return store;
    }

    public List<OrderStore.OperatorOrder> operatorOrders() throws SQLException {
        return orderStore.loadOperatorOrders();
    }

    /** Changes the live city only; a reset or restart returns the store to its configured status. */
    public synchronized GroceryStore setStoreStatus(String storeId, StoreStatus status) {
        GroceryStore store = findStore(storeId);
        store.setStatus(status);
        return store;
    }

    /** Reset: deletes every saved order and swaps in a freshly loaded city. */
    public synchronized void reset(CitySnapshot freshCity) throws SQLException {
        orderStore.deleteAllOrders();
        cityCatalog.replace(freshCity);
        deliveries.clear();
        // DLV keeps courier status and position in the database; put them back to the initial city.
        for (CourierPin courier : freshCity.couriers()) {
            Long userId = accountRepository.courierUserId(courier.id());
            if (userId == null) continue;
            accountRepository.updateCourierStatus(userId, courier.status().label());
            accountRepository.updateCourierLocation(userId, courier.lat(), courier.lon());
        }
        customers.clear();
        savedOrdersByCustomer.clear();
        // Groceries accounts still point at the old stores; move each one to its matching new store.
        for (GroceriesAccount groceries : groceriesAccounts.values()) {
            groceries.setStore(findStore(groceries.getStore().getId()));
        }
    }

    /** Pause a store: its own groceries account, or the operator for any store (R-DASH-01). */
    public synchronized GroceryStore pauseStore(AuthenticatedAccount account, String storeId) {
        GroceryStore store = storeAllowedToPause(account, storeId);
        store.pause();
        return store;
    }

    /** Reopen a paused store (the dispatcher already checked that this is the operator). */
    public synchronized GroceryStore reopenStore(String storeId) {
        GroceryStore store = findStore(storeId);
        store.reopen();
        return store;
    }

    private GroceryStore storeAllowedToPause(AuthenticatedAccount account, String storeId) {
        if (account != null && account.role() == Role.OPERATOR) {
            return findStore(storeId);
        }
        if (account == null || account.role() != Role.GROCERIES) {
            throw new SecurityException("Only the store's staff or the operator can pause a grocery store.");
        }
        GroceryStore ownStore = storeFor(account); // refuses anyone who is not a groceries account
        if (!ownStore.getId().equals(storeId)) {
            throw new SecurityException("You can only pause your own grocery store.");
        }
        return ownStore;
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
            customer.setDefaultAddress(stored.defaultAddress());
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
            order.setCustomerUserId(saved.customerId());
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
                String pin = accountRepository.courierPinForUser(saved.courierUserId());
                rememberCourierStatus(saved.courierUserId(), pin, CourierStatus.CARRYING);
                rememberCourierLocation(saved.courierUserId(), pin, store.getLat(), store.getLon());
            }
            savedOrdersByCustomer.computeIfAbsent(saved.customerId(), id -> new ArrayList<>()).add(order);
        }
        offerUnassignedReadyOrders();
    }
}
