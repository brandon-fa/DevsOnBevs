package com.littleisles.domain.common;

public class Billing {
    private static Billing instance;
    private final double taxRate = 14.975;
    private final double deliveryFeePerKM = 1.0;
    private final double baseDeliveryFee = 3.0;
    private final double platformFee = 3.0;

    public static Billing getInstance() {
        if (instance == null) {
            instance = new Billing();
        }
        return instance;
    }

    public double calculateTip(double totalPrice, double tipPercentage) {
        return totalPrice * (tipPercentage / 100);
    }

    public double calculateTax(double totalPrice) {
        return totalPrice * (taxRate / 100);
    }

    //To implement with working geolocation services
    /*
    public double calculateDeliveryFee(double distanceInKM) {
        return baseDeliveryFee + (deliveryFeePerKM * distanceInKM);
    }*/

    //Placeholder for above
    public double calculateDeliveryFee(double distanceInKM) {
        return baseDeliveryFee;
    }

    public double getPlatformFee() {
        return platformFee;
    }
}
