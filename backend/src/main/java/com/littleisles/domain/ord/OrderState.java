package com.littleisles.domain.ord;

public abstract class OrderState {
    protected Order order;

    public OrderState(Order order) {
        this.order = order;
    }

    public abstract String getStatus();

    public abstract void addOrderLine(Product product, int quantity);

    public abstract void removeOrderLine(Product product, int quantity);

    public abstract void setDestination(String destination);

    public abstract void placeOrder();

    public abstract void cancelOrder();

    public void rejectOrder(String reason) {
        throw new IllegalStateException("Only a pending order can be rejected.");
    }

    public abstract void acceptOrder();

    public abstract void readyOrder();

    public abstract void pickupOrder();

    public abstract void handoffOrder();
}
