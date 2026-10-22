package com.littleisles.domain.ord;

import java.util.ArrayList;

public class PreparationQueue {
    private ArrayList<Order> orders;

    public PreparationQueue() {
        this.orders = new ArrayList<>();
    }

    public void addOrder(Order order) {
        orders.add(order);
    }

    public void removeOrder() {
        orders.remove(0);
    }

    public void removeOrder(Order order) {
        orders.remove(order);
    }

    public Order getNextOrder() {
        if (!orders.isEmpty()) {
            return orders.get(0);
        }
        return null;
    }

}
