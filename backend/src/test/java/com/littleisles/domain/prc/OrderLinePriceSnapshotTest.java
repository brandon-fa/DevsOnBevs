package com.littleisles.domain.prc;

import com.littleisles.domain.ord.OrderLine;
import com.littleisles.domain.ord.Product;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderLinePriceSnapshotTest {
    @Test
    void keepsTheUnitPriceFromWhenTheLineWasCreated() {
        Product product = new Product("rice", "Rice", 10.00);
        OrderLine line = new OrderLine(product, 2);

        product.setPrice(12.00);

        assertEquals(1_000, line.getUnitPriceCents());
    }
}
