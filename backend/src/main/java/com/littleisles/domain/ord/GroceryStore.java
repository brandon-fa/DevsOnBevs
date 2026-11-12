package com.littleisles.domain.ord;

import java.util.ArrayList;
import java.util.List;

public class GroceryStore {
    private static int nextStoreId = 1;
    private int storeId;
    private final String id;
    private String name;
    private final ArrayList<Order> pendingOrders = new ArrayList<>();
    private final ArrayList<Order> readyOrders = new ArrayList<>();
    private final PreparationQueue preparationQueue = new PreparationQueue();
    private String location;
    private double lat;
    private double lon;
    private StoreStatus status = StoreStatus.OPEN;
    private final ArrayList<Product> products = new ArrayList<>();

    public GroceryStore(String name, String location) {
        this.storeId = nextStoreId++;
        this.id = Integer.toString(storeId);
        this.name = name;
        this.location = location;
    }

    public GroceryStore(String id, String name, double lat, double lon) {
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("A grocery store needs an id and name.");
        }
        this.storeId = nextStoreId++;
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.location = lat + ", " + lon;
    }

    public String getId() {
        return id;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public void addProduct(Product product) {
        products.add(product);
    }

    public void removeProduct(Product product) {
        products.remove(product);
    }

    public void addPendingOrder(Order order) {
        pendingOrders.add(order);
    }

    public void restoreReadyOrder(Order order) {
        readyOrders.add(order);
    }

    public void acceptPendingOrder(Order order) {
        if (order == null || order.getStore() != this || !pendingOrders.contains(order)
                || !(order.getState() instanceof PlacedOrderState)) {
            throw new IllegalStateException("This order is no longer pending at your store.");
        }
        order.acceptOrder();
        addOrderToQueue(order);
        pendingOrders.remove(order);
    }

    public void cancelPendingOrder(Order order) {
        pendingOrders.remove(order);
    }

    public void rejectPendingOrder(Order order, String reason) {
        if (order == null || order.getStore() != this || !pendingOrders.contains(order)
                || !(order.getState() instanceof PlacedOrderState)) {
            throw new IllegalStateException("This order is no longer pending at your store.");
        }
        order.rejectOrder(reason);
    }

    public void addOrderToQueue(Order order) {
        preparationQueue.addOrder(order);
    }

    public void removeOrderFromQueue(Order order) {
        order.cancelOrder();
        preparationQueue.removeOrder(order);
    }

    public void readyNextOrder() {
        Order nextOrder = preparationQueue.getNextOrder();
        if (nextOrder != null) {
            readyNextOrder(nextOrder.getOrderId());
        }
    }

    public void readyNextOrder(int expectedOrderId) {
        Order nextOrder = preparationQueue.getNextOrder();
        if (nextOrder == null || nextOrder.getOrderId() != expectedOrderId
                || !(nextOrder.getState() instanceof PreparingOrderState)) {
            throw new IllegalStateException("Only the next order in the preparation queue can be marked ready.");
        }
        nextOrder.readyOrder();
        preparationQueue.removeOrder();
        readyOrders.add(nextOrder);
    }

    public List<Order> getReadyOrders() {
        ArrayList<Order> result = new ArrayList<>();
        for (Order order : readyOrders) {
            if (order.getState() instanceof ReadyOrderState) {
                result.add(order);
            }
        }
        return List.copyOf(result);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PreparationQueue getPreparationQueue() {
        return preparationQueue;
    }

    /** R-DASH-03: number of accepted orders waiting in the preparation queue. */
    public int getQueuedOrderCount() {
        return preparationQueue.getOrders().size();
    }

    /** R-DASH-03: the colour band of this store's queue. */
    public QueueBand getQueueBand() {
        return QueueBand.of(getQueuedOrderCount(), isPaused());
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

        /** R-ORD-02: only an open store accepts new orders. */
    public boolean isOpen() {
        return status == StoreStatus.OPEN;
    }

    public boolean isPaused() {
        return status == StoreStatus.PAUSED;
    }

    public StoreStatus getStatus() {
        return status;
    }

    public void setStatus(StoreStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("A grocery store needs a status.");
        }
        this.status = status;
    }
    /** Pause: stop taking new orders but keep preparing the accepted ones (R-ORD-02). */
    public void pause() {
        if (status != StoreStatus.OPEN) {
            throw new IllegalStateException("Only an open grocery store can be paused.");
        }
        status = StoreStatus.PAUSED;
    }
    /** Reopen: a paused store takes new orders again. */
    public void reopen() {
        if (status != StoreStatus.PAUSED) {
            throw new IllegalStateException("Only a paused grocery store can be reopened.");
        }
        status = StoreStatus.OPEN;
    }

     public int getStoreId() {
        return storeId;
    }

    public void setStoreId(int storeId) {
        this.storeId = storeId;
    }

    public ArrayList<Order> getPendingOrders() {
        return pendingOrders;
    }

    public ArrayList<Product> getProducts() {
        return products;
    }

}
