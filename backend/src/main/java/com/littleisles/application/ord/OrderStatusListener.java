package com.littleisles.application.ord;

import com.littleisles.domain.ord.Order;

/**
 * Extension point: told every time OrderController saves a change to an order's status.
 * Modules that react to order progress (for example notifications) implement this and are
 * registered once at startup, so OrderController does not depend on them.
 */
@FunctionalInterface
public interface OrderStatusListener {
    /** Called after the change is saved; {@code status} is the status that was saved. */
    void orderStatusChanged(Order order, String status);
}