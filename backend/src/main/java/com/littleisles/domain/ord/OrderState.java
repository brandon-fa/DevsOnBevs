package com.littleisles.domain.ord;

public abstract class OrderState {
    protected Order order;

    public OrderState(Order order) {
        this.order = order;
    }

    public abstract void addOrderLine(Product product, int quantity);

    public abstract void removeOrderLine(Product product, int quantity);

    public abstract void setDestination(String destination);

    public abstract void placeOrder();

    public abstract void cancelOrder();

    public abstract void acceptOrder();

    public abstract void readyOrder();

    public abstract void pickupOrder();

    public abstract void handoffOrder();
}
