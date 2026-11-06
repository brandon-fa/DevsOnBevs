package com.littleisles.application.dash;

import com.littleisles.application.ord.OrderController;
import com.littleisles.domain.ord.CourierRosterEntry;
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

    public List<Order> courierOffers(AuthenticatedAccount account) throws SQLException {
        return orderController.courierOffers(account);
    }

    public void acceptCourierOffer(AuthenticatedAccount account, int orderId) throws SQLException {
        orderController.acceptCourierOffer(account, orderId);
    }

    public void pickupCourierOrder(AuthenticatedAccount account, int orderId) throws SQLException {
        orderController.pickupCourierOrder(account, orderId);
    }

    public void deliverCourierOrder(AuthenticatedAccount account, int orderId) throws SQLException {
        orderController.deliverCourierOrder(account, orderId);
    }

    public String cityJson(AuthenticatedAccount account) throws SQLException {
        return orderController.cityJson(account);
    }

    public List<CourierRosterEntry> operatorCouriers(AuthenticatedAccount account) throws SQLException {
        return orderController.operatorCouriers(account);
    }

    public List<Order> operatorOrders(AuthenticatedAccount account) throws SQLException {
        return orderController.operatorOrders(account);
    }

    public void reassignAcceptedOrder(AuthenticatedAccount account, int orderId, long courierUserId) throws SQLException {
        orderController.reassignAcceptedOrder(account, orderId, courierUserId);
    }

    private static void requireCustomer(AuthenticatedAccount account) {
        if (account == null || account.role() != Role.CUSTOMER) {
            throw new SecurityException("Only customers can place or view their orders.");
        }
    }
}
