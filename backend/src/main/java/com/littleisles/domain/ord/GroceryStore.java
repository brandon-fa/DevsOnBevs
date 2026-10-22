package com.littleisles.domain.ord;

import java.util.ArrayList;

public class GroceryStore {
    private String name;
    private ArrayList<Order> pendingOrders;
    private PreparationQueue preparationQueue;
    private String location;

    public GroceryStore(String name, String location) {
        this.name = name;
        this.location = location;
        this.preparationQueue = new PreparationQueue();
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


}