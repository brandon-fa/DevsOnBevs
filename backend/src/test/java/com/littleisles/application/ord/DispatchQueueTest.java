package com.littleisles.application.ord;

import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.ord.Order;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DispatchQueueTest {
    @Test
    void preservesOrderAndDoesNotEnqueueTheSameOrderTwice() {
        DispatchQueue queue = new DispatchQueue();
        GroceryStore store = new GroceryStore("market", "Market", 45.5, -73.6);
        Order first = new Order(store, "First destination");
        Order second = new Order(store, "Second destination");

        queue.enqueue(first);
        queue.enqueue(second);
        queue.enqueue(first);

        assertEquals(List.of(first, second), queue.snapshot());
    }

    @Test
    void removesOrderAfterItIsAssigned() {
        DispatchQueue queue = new DispatchQueue();
        Order order = new Order(new GroceryStore("market", "Market", 45.5, -73.6), "Destination");
        queue.enqueue(order);

        queue.remove(order);

        assertEquals(List.of(), queue.snapshot());
    }

    @Test
    void rejectsNullOrders() {
        DispatchQueue queue = new DispatchQueue();

        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(null));
    }
}
