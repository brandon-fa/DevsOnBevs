package com.littleisles.domain.ord;

public class OrderLine {
        private Product product;
        private int quantity;
        private long unitPriceCents;

        public OrderLine(Product product, int quantity) {
            this(product, quantity, priceInCents(product));
        }

        public OrderLine(Product product, int quantity, long unitPriceCents) {
            if (product == null) {
                throw new IllegalArgumentException("An order line needs a product.");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("An order-line quantity must be positive.");
            }
            if (unitPriceCents < 0) {
                throw new IllegalArgumentException("An order-line unit price cannot be negative.");
            }
            this.product = product;
            this.quantity = quantity;
            this.unitPriceCents = unitPriceCents;
        }

        public Product getProduct() {
            return product;
        }

        public void setProduct(Product product) {
            if (product == null) {
                throw new IllegalArgumentException("An order line needs a product.");
            }
            this.product = product;
            this.unitPriceCents = priceInCents(product);
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("An order-line quantity must be positive.");
            }
            this.quantity = quantity;
        }

        public long getUnitPriceCents() {
            return unitPriceCents;
        }

        private static long priceInCents(Product product) {
            if (product == null) {
                throw new IllegalArgumentException("An order line needs a product.");
            }
            return Math.round(product.getPrice() * 100.0);
        }
}
