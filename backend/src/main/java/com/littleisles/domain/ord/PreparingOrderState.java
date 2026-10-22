package com.littleisles.domain.ord;

public class PreparingOrderState extends OrderState {

    public PreparingOrderState(Order order) {
        super(order);
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
    }

    public void acceptOrder(){
    }

    public void readyOrder(){
        order.setState(new ReadyOrderState(order));
    }

    public void pickupOrder(){
    }

    public void handoffOrder(){
    }
    
}
