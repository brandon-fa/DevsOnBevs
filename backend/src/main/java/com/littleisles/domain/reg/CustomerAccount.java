package com.littleisles.domain.reg;

import java.util.ArrayList;

import com.littleisles.domain.ord.Order;

public final class CustomerAccount extends Account {
    private ArrayList<Order> orders;

    public CustomerAccount(String fullName, String email, String passwordHash, String phone) {
        super(fullName, email, passwordHash, phone, Role.CUSTOMER);
        this.orders = new ArrayList<Order>();
    }

    public void addOrder(Order order) {
        orders.add(order);
    }

    public ArrayList<Order> getOrders() {
        return orders;
    }
}
