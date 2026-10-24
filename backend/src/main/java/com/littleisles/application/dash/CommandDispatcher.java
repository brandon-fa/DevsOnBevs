package com.littleisles.application.dash;

import com.littleisles.application.ord.OrderController;
import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.Role;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public final class CommandDispatcher {
    private final OrderController orderController;

    public CommandDispatcher(OrderController orderController) {
        this.orderController = orderController;
    }

    public Order placeOrder(AuthenticatedAccount account, String storeId, String destination,
                            Map<String, Integer> products) throws SQLException {
        requireCustomer(account);
        return orderController.placeOrder(account, storeId, destination, products);
    }

    public List<Order> customerOrders(AuthenticatedAccount account) throws SQLException {
        requireCustomer(account);
        return orderController.customerOrders(account);
    }

    public GroceryStore acceptPendingOrder(AuthenticatedAccount account, int orderId) {
        return orderController.acceptPendingOrder(account, orderId);
    }

    public GroceryStore readyNextOrder(AuthenticatedAccount account, int orderId) {
        return orderController.readyNextOrder(account, orderId);
    }

    public GroceryStore rejectPendingOrder(AuthenticatedAccount account, int orderId, String reason) {
        return orderController.rejectPendingOrder(account, orderId, reason);
    }

    public GroceryStore storeOrders(AuthenticatedAccount account) {
        return orderController.storeOrders(account);
    }

    private static void requireCustomer(AuthenticatedAccount account) {
        if (account == null || account.role() != Role.CUSTOMER) {
            throw new SecurityException("Only customers can place or view their orders.");
        }
    }
}
