package com.littleisles.application.dash;

import com.littleisles.application.ord.OrderController;
import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.Role;
import com.littleisles.technicalservices.ord.CityConfigLoader;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public final class CommandDispatcher {
    private final OrderController orderController;
    private final CityConfigLoader cityLoader;

    public CommandDispatcher(OrderController orderController, CityConfigLoader cityLoader) {
        this.orderController = orderController;
        this.cityLoader = cityLoader;
    }

    public Order placeOrder(AuthenticatedAccount account, String storeId, String destination,
                            Map<String, Integer> products, boolean saveAsDefault) throws SQLException {
        requireCustomer(account);
        return orderController.placeOrder(account, storeId, destination, products, saveAsDefault);
    }

    public List<Order> customerOrders(AuthenticatedAccount account) throws SQLException {
        requireCustomer(account);
        return orderController.customerOrders(account);
    }

    public GroceryStore acceptPendingOrder(AuthenticatedAccount account, int orderId) throws SQLException {
        return orderController.acceptPendingOrder(account, orderId);
    }

    public GroceryStore readyNextOrder(AuthenticatedAccount account, int orderId) throws SQLException {
        return orderController.readyNextOrder(account, orderId);
    }

    public GroceryStore rejectPendingOrder(AuthenticatedAccount account, int orderId, String reason) throws SQLException {
        return orderController.rejectPendingOrder(account, orderId, reason);
    }

    public GroceryStore storeOrders(AuthenticatedAccount account) {
        return orderController.storeOrders(account);
    }

    /** Reset (operator only): restore the initial scenario from the configuration file. */
    public void reset(AuthenticatedAccount account) throws IOException, SQLException {
        requireOperator(account);
        orderController.reset(cityLoader.load());
    }

    private static void requireCustomer(AuthenticatedAccount account) {
        if (account == null || account.role() != Role.CUSTOMER) {
            throw new SecurityException("Only customers can place or view their orders.");
        }
    }
    
    private static void requireOperator(AuthenticatedAccount account) {
        if (account == null || account.role() != Role.OPERATOR) {
            throw new SecurityException("Only the platform operator can reset the simulator.");
        }
    }
}
