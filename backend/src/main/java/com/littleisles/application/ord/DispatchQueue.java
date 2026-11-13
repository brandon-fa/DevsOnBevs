package com.littleisles.application.ord;

import com.littleisles.domain.ord.Order;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DispatchQueue {
    private final Set<Order> orders = new LinkedHashSet<>();

    public synchronized void enqueue(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("A dispatch queue entry needs an order.");
        }
        orders.add(order);
    }

    public synchronized void remove(Order order) {
        orders.remove(order);
    }

    public synchronized List<Order> snapshot() {
        return List.copyOf(new ArrayList<>(orders));
    }
}
