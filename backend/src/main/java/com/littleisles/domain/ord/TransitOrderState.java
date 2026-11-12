package com.littleisles.domain.ord;

public class TransitOrderState extends OrderState {

    public TransitOrderState(Order order) {
        super(order);
    }

    public String getStatus() {
        return "picked_up";
    }

    public void addOrderLine(Product product, int quantity) {
    }

    public void removeOrderLine(Product product, int quantity) {
    }

    public void setDestination(String destination) {
    }

    public void placeOrder() {
    }

    public void cancelOrder() {
    }

    public void acceptOrder() {
    }

    public void readyOrder() {
    }

    public void pickupOrder() {
    }

    public void handoffOrder() {
        order.setState(new CompletedOrderState(order));
        order.addTransitionEvent(new TransitionEvent("picked_up", "delivered"));
    }
}
