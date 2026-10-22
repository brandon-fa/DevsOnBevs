package com.littleisles.domain.ord;

public class PlacedOrderState extends OrderState {

    public PlacedOrderState(Order order) {
        super(order);
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
    }

    public void acceptOrder(){
        order.setState(new PreparingOrderState(order));
    }

    public void readyOrder(){
    }

    public void pickupOrder(){
    }

    public void handoffOrder(){
    }
}