package com.littleisles.technicalservices.ord;

import com.littleisles.domain.ord.Order;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for order use cases (UC-ORD-02 and UC-ORD-03). */
public interface OrderRepository {
    /** Inserts a new order and returns it with its persistent id assigned. */
    Order insert(Order order) throws SQLException;

    /** Persists an update to an order already returned by {@link #insert(Order)}. */
    void update(Order order) throws SQLException;

    Optional<Order> findById(long orderId) throws SQLException;

    /**
     * Returns only accepted orders for the restaurant, oldest acceptance first.
     * Implementations should use the order's accepted-at time and a stable
     * tie-breaker (such as order id) when two timestamps are equal.
     */
    List<Order> findAcceptedByRestaurantInArrivalOrder(long restaurantId) throws SQLException;
}
