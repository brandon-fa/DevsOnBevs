package com.littleisles.domain.ord;

public final class CompletedOrderState extends OrderState {
    public CompletedOrderState(Order order) {
        super(order);
    }

    @Override
    public String getStatus() {
        return "delivered";
    }

    @Override
    public void addOrderLine(Product product, int quantity) {
        throw deliveredOrderIsReadOnly();
    }

    @Override
    public void removeOrderLine(Product product, int quantity) {
        throw deliveredOrderIsReadOnly();
    }

    @Override
    public void setDestination(String destination) {
        throw deliveredOrderIsReadOnly();
    }

    @Override
    public void placeOrder() {
        throw deliveredOrderIsReadOnly();
    }

    @Override
    public void cancelOrder() {
        throw deliveredOrderIsReadOnly();
    }

    @Override
    public void acceptOrder() {
        throw deliveredOrderIsReadOnly();
    }

    @Override
    public void readyOrder() {
        throw deliveredOrderIsReadOnly();
    }

    @Override
    public void pickupOrder() {
        throw deliveredOrderIsReadOnly();
    }

    @Override
    public void handoffOrder() {
        throw deliveredOrderIsReadOnly();
    }

    private static IllegalStateException deliveredOrderIsReadOnly() {
        return new IllegalStateException("A delivered order cannot be changed.");
    }
}
