package com.littleisles.domain.reg;

import java.util.ArrayList;

import com.littleisles.domain.ord.Order;

public final class CustomerAccount extends Account {
    private ArrayList<Order> orders;
    private String defaultAddress;

    public CustomerAccount(String fullName, String email, String passwordHash, String phone) {
        super(fullName, email, passwordHash, phone, Role.CUSTOMER);
        this.orders = new ArrayList<Order>();
    }

    public void setDefaultAddress(String defaultAddress) {
        this.defaultAddress = defaultAddress == null || defaultAddress.isBlank() ? null : defaultAddress.trim();
    }

    public String getDefaultAddress() {
        return defaultAddress;
    }

    public void addOrder(Order order) {
        orders.add(order);
    }

    public void removeOrder(Order order) {
        orders.remove(order);
    }

    public ArrayList<Order> getOrders() {
        return orders;
    }
}
