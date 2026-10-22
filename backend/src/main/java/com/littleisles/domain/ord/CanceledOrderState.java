package com.littleisles.domain.ord;

public class CanceledOrderState extends OrderState {

    public CanceledOrderState(Order order) {
        super(order);
    }

    public void addOrderLine(Product product, int quantity){
        for (OrderLine orderLine : order.getOrderLines()) {
            if (orderLine.getProduct().equals(product)) {
                orderLine.setQuantity(orderLine.getQuantity() + quantity);
                return;
            }
        }
        order.getOrderLines().add(new OrderLine(product, quantity));
    }

    public void removeOrderLine(Product product, int quantity){
        for (OrderLine orderLine : order.getOrderLines()) {
            if (orderLine.getProduct().equals(product)) {
                int newQuantity = orderLine.getQuantity() - quantity;
                if (newQuantity <= 0) {
                    order.getOrderLines().remove(orderLine);
                } else {
                    orderLine.setQuantity(newQuantity);
                }
                return;
            }
        }
    }

    public void setDestination(String destination){
        order.setDestination(destination);
    }

    public void placeOrder(){
        order.getStore().addPendingOrder(order);
        order.setState(new PlacedOrderState(order));
    }

    public void cancelOrder(){
        order.getOrderLines().clear();
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
