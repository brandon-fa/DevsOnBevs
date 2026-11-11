package com.littleisles.domain.ord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenOrderStateTest {

    @Test
    void clearsLinesWithoutCancelingWhenLastLineIsRemoved() {
        GroceryStore store = new GroceryStore("olive-rye", "Olive & Rye", 45.5, -73.6);
        Product olives = new Product("olives", "Green Olives", 4.5);
        Product bread = new Product("bread", "Sesame Bread", 3.0);
        Order order = new Order(store, "4918 Allee des Sorbiers");

        order.addOrderLine(olives, 2);
        order.addOrderLine(bread, 1);

        order.removeOrderLine(olives, 2);
        assertEquals("open", order.getStatus());
        assertEquals(1, order.getOrderLines().size());

        order.removeOrderLine(bread, 1);
        assertTrue(order.getOrderLines().isEmpty());
        assertEquals("open", order.getStatus());
    }

    @Test
    void keepsOrderWhenQuantityRemains() {
        GroceryStore store = new GroceryStore("olive-rye", "Olive & Rye", 45.5, -73.6);
        Product olives = new Product("olives", "Green Olives", 4.5);
        Order order = new Order(store, "4918 Allee des Sorbiers");

        order.addOrderLine(olives, 3);
        order.removeOrderLine(olives, 1);

        assertEquals("open", order.getStatus());
        assertEquals(1, order.getOrderLines().size());
        assertEquals(2, order.getOrderLines().get(0).getQuantity());
    }
}
