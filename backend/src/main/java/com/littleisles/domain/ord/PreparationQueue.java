package com.littleisles.domain.ord;

import java.util.ArrayList;
import java.util.List;

public class PreparationQueue {
    private ArrayList<Order> orders;

    public PreparationQueue() {
        this.orders = new ArrayList<>();
    }

    public void addOrder(Order order) {
        if (order == null || !(order.getState() instanceof PreparingOrderState) || orders.contains(order)) {
            throw new IllegalStateException("Only an accepted order can be added to the preparation queue once.");
        }
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

    public List<Order> getOrders() {
        return List.copyOf(orders);
    }

}
