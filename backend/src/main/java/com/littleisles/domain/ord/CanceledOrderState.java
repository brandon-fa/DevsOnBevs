package com.littleisles.domain.ord;

public class CanceledOrderState extends OrderState {

    public CanceledOrderState(Order order) {
        super(order);
    }

    public String getStatus() {
        return "canceled";
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
    }

    public void acceptOrder(){
    }

    public void readyOrder(){
    }

    public void pickupOrder(){
    }

    public void handoffOrder(){
    }

}
