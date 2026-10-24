package com.littleisles.domain.ord;

public class OpenOrderState extends OrderState {

    public OpenOrderState(Order order) {
        super(order);
    }

    public String getStatus() {
        return "open";
    }
    
    public void addOrderLine(Product product, int quantity){
        for (OrderLine orderLine : order.getOrderLines()) {
            if (orderLine.getProduct().getId().equals(product.getId())) {
                orderLine.setQuantity(orderLine.getQuantity() + quantity);
                return;
            }
        }
        order.getOrderLines().add(new OrderLine(product, quantity));
    }

    public void removeOrderLine(Product product, int quantity){
        for (OrderLine orderLine : order.getOrderLines()) {
            if (orderLine.getProduct().getId().equals(product.getId())) {
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
        order.addTransitionEvent(new TransitionEvent("open", "placed"));
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
