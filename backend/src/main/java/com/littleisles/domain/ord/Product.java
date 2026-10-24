package com.littleisles.domain.ord;

import java.util.UUID;

public class Product {
    private final String id;
    private String name;
    private double price;
    private boolean soldOut;

    public Product(String name, double price) {
        this(UUID.randomUUID().toString(), name, price);
    }

    public Product(String id, String name, double price) {
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("A product needs an id and name.");
        }
        if (!Double.isFinite(price) || price < 0) {
            throw new IllegalArgumentException("A product price must be a finite, non-negative number.");
        }
        this.id = id;
        this.name = name;
        this.price = price;
        this.soldOut = false;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public boolean isSoldOut() {
        return soldOut;
    }

    public void setSoldOut(boolean soldOut) {
        this.soldOut = soldOut;
    }

    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || this.getClass() != obj.getClass()) return false;
        Product product = (Product) obj;
        return name.equalsIgnoreCase(product.name);
    }
}
