package com.littleisles.domain.ord;

public class PlacedOrderState extends OrderState {

    public PlacedOrderState(Order order) {
        super(order);
    }

    public String getStatus() {
        return "placed";
    }
    
    public void addOrderLine(Product product, int quantity){
    }

    public void removeOrderLine(Product product, int quantity){
    }

    public void setDestination(String destination){
        //To be implemented once geolocation is implemented
    }

    public void placeOrder(){
    }

    public void cancelOrder(){
        order.getStore().cancelPendingOrder(order);
        order.setState(new CanceledOrderState(order));
        order.addTransitionEvent(new TransitionEvent("placed", "canceled"));
    }

    public void acceptOrder(){
        order.setState(new PreparingOrderState(order));
        order.addTransitionEvent(new TransitionEvent("placed", "preparing"));
    }

    public void rejectOrder(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Enter a reason for rejecting the order.");
        }
        String rejectionReason = reason.trim();
        order.getStore().cancelPendingOrder(order);
        order.setRejectionReason(rejectionReason);
        order.setState(new CanceledOrderState(order));
        order.addTransitionEvent(new TransitionEvent("placed", "canceled", rejectionReason));
    }

    public void readyOrder(){
    }

    public void pickupOrder(){
    }

    public void handoffOrder(){
    }
}
