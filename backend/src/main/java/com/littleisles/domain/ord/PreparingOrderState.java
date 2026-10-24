package com.littleisles.domain.ord;

public class PreparingOrderState extends OrderState {

    public PreparingOrderState(Order order) {
        super(order);
    }

    public String getStatus() {
        return "preparing";
    }
    
    public void addOrderLine(Product product, int quantity){
    }

    public void removeOrderLine(Product product, int quantity){
    }

    public void setDestination(String destination){
    }

    public void placeOrder(){
    }

    public void cancelOrder(){
        order.getStore().cancelPendingOrder(order);
        order.setState(new CanceledOrderState(order));
        order.addTransitionEvent(new TransitionEvent("preparing", "canceled"));
    }

    public void acceptOrder(){
    }

    public void readyOrder(){
        if (order.getStore().getPreparationQueue().getNextOrder() != order) {
            throw new IllegalStateException("Only the next order in the preparation queue can be marked ready.");
        }
        order.setState(new ReadyOrderState(order));
        order.addTransitionEvent(new TransitionEvent("preparing", "ready"));
    }

    public void pickupOrder(){
    }

    public void handoffOrder(){
    }
    
}
