package com.littleisles.domain.ord;

public class ReadyOrderState extends OrderState {

    public ReadyOrderState(Order order) {
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
    }

    public void pickupOrder(){
        //To be implemented in next phase
    }

    public void handoffOrder(){
    }
    
}
