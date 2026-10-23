package com.littleisles.domain.ord;

import java.util.ArrayList;

public class GroceryStore {
    private static int nextStoreId = 1;
    private int storeId;
    private String name;
    private ArrayList<Order> pendingOrders;
    private PreparationQueue preparationQueue;
    private String location;
    private boolean isOpen;
    private ArrayList<Product> products;

    public GroceryStore(String name, String location) {
        this.storeId = nextStoreId++;
        this.name = name;
        this.location = location;
        this.isOpen = true;
        this.preparationQueue = new PreparationQueue();
        this.pendingOrders = new ArrayList<Order>();
        this.products = new ArrayList<Product>();
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

    public void acceptPendingOrder(Order order) {
        order.acceptOrder();
        addOrderToQueue(order);
        pendingOrders.remove(order);
    }

    public void cancelPendingOrder(Order order) {
        pendingOrders.remove(order);
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
            nextOrder.readyOrder();
            preparationQueue.removeOrder();
        }
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        isOpen = open;
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