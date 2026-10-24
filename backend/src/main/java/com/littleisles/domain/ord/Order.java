package com.littleisles.domain.ord;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.littleisles.domain.common.Billing;

public class Order {
    private static final AtomicInteger nextOrderId = new AtomicInteger(1);
    private int orderId;
    private GroceryStore store;
    private OrderState state;
    private ArrayList<OrderLine> orderLines;
    private ArrayList<TransitionEvent> transitionEvents;
    private String destination;
    private String rejectionReason;
    //private double tip;

    public Order(GroceryStore store, String destination) {
        this.orderId = nextOrderId.getAndIncrement();
        this.store = store;
        this.destination = destination;
        this.state = new OpenOrderState(this);
        this.orderLines = new ArrayList<OrderLine>();
        this.transitionEvents = new ArrayList<TransitionEvent>();
    }

    public void setState(OrderState state) {
        this.state = state;
    }

    public void addOrderLine(Product product, int quantity) {
        state.addOrderLine(product, quantity);
    }

    public void removeOrderLine(Product product, int quantity) {
        state.removeOrderLine(product, quantity);
    }

    public void setDestination(String destination) {
        state.setDestination(destination);
    }

    public void placeOrder() {
        state.placeOrder();
    }

    public void cancelOrder() {
        state.cancelOrder();
    }

    public void rejectOrder(String reason) {
        state.rejectOrder(reason);
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    void setRejectionReason(String reason) {
        rejectionReason = reason;
    }

    public void acceptOrder() {
        state.acceptOrder();
    }

    public void readyOrder() {
        state.readyOrder();
    }

    public void pickupOrder() {
        state.pickupOrder();
    }

    public void handoffOrder() {
        state.handoffOrder();
    }

    /*
    public double[] getTotalPrice(){
        double subtotal = 0.0;
        for (OrderLine orderLine : orderLines) {
            subtotal += orderLine.product.getPrice() * orderLine.quantity;
        }
        double tax = Billing.getInstance().calculateTax(subtotal);
        double deliveryFee = Billing.getInstance().calculateDeliveryFee(0.0); // Placeholder for distance
        double platformFee = Billing.getInstance().getPlatformFee();
        double totalPrice = subtotal + tax + deliveryFee + platformFee + tip;
        return new double[]{totalPrice, subtotal, tax, deliveryFee, platformFee, tip};
    }
    */

    public GroceryStore getStore() {
        return store;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setStore(GroceryStore store) {
        this.store = store;
    }

    public OrderState getState() {
        return state;
    }

    public String getStatus() {
        return state.getStatus();
    }

    public ArrayList<OrderLine> getOrderLines() {
        return orderLines;
    }

    public void setOrderLines(ArrayList<OrderLine> orderLines) {
        this.orderLines = orderLines;
    }

    public String getDestination() {
        return destination;
    }

    /*
    public double getTip() {
        return tip;
    }

    public void setTip(double tip) {
        this.tip = tip;
    }
    */

    public ArrayList<TransitionEvent> getTransitionEvents() {
        return transitionEvents;
    }

    public void addTransitionEvent(TransitionEvent event) {
        transitionEvents.add(Objects.requireNonNull(event, "A transition event cannot be null."));
    }
    
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Order order = (Order) obj;
        return orderId == order.orderId;
    }
}
