package com.littleisles.domain.ord;

public class Product {
    private String name;
    private double price;
    private boolean soldOut;

    public Product(String name, double price) {
        this.name = name;
        this.price = price;
        this.soldOut = false;
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
