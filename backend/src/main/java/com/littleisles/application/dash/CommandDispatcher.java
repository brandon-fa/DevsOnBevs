package com.littleisles.application.dash;

import com.littleisles.application.ord.OrderController;
import com.littleisles.domain.ord.CourierRosterEntry;
import com.littleisles.domain.ord.CourierStatus;
import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.ord.StoreStatus;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.Role;
import com.littleisles.infrastructure.db.OrderStore;
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

    public List<OrderStore.OperatorOrder> operatorOrders(AuthenticatedAccount account) throws SQLException {
        requireOperator(account, "view all orders");
        return orderController.operatorOrders();
    }

    public GroceryStore setStoreOpen(AuthenticatedAccount account, String storeId, boolean open) {
        requireOperator(account, "open or close a grocery store");
        return orderController.setStoreStatus(storeId, open ? StoreStatus.OPEN : StoreStatus.CLOSED);
    }

    /** Reset (operator only): restore the initial scenario from the configuration file. */
    public void reset(AuthenticatedAccount account) throws IOException, SQLException {
        requireOperator(account, "reset the simulator");
        orderController.reset(cityLoader.load());
    }

    public GroceryStore pauseStore(AuthenticatedAccount account, String storeId) {
        return orderController.pauseStore(account, storeId);
    }

    public GroceryStore reopenStore(AuthenticatedAccount account, String storeId) {
        requireOperator(account, "reopen a grocery store");
        return orderController.reopenStore(storeId);
    }
    
    public List<Order> courierOffers(AuthenticatedAccount account) throws SQLException {
        return orderController.courierOffers(account);
    }

    public CourierStatus courierStatus(AuthenticatedAccount account) throws SQLException {
        return orderController.courierStatus(account);
    }

    public CourierStatus setCourierOnline(AuthenticatedAccount account, boolean online) throws SQLException {
        return orderController.setCourierOnline(account, online);
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

    public List<Order> operatorCourierOrders(AuthenticatedAccount account) throws SQLException {
        return orderController.operatorCourierOrders(account);
    }

    public void reassignAcceptedOrder(AuthenticatedAccount account, int orderId, long courierUserId) throws SQLException {
        orderController.reassignAcceptedOrder(account, orderId, courierUserId);
    }

    private static void requireCustomer(AuthenticatedAccount account) {
        if (account == null || account.role() != Role.CUSTOMER) {
            throw new SecurityException("Only customers can place or view their orders.");
        }
    }

    private static void requireOperator(AuthenticatedAccount account, String action) {
        if (account == null || account.role() != Role.OPERATOR) {
            throw new SecurityException("Only the platform operator can " + action + ".");
        }
    }
}
