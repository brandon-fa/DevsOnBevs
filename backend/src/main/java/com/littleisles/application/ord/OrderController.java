package com.littleisles.application.ord;

import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.OrderLine;
import com.littleisles.domain.ord.Product;
import com.littleisles.domain.reg.AccountCredentials;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.CustomerAccount;
import com.littleisles.domain.reg.GroceriesAccount;
import com.littleisles.domain.reg.Role;
import com.littleisles.technicalservices.reg.AccountRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OrderController {
    private final CityCatalog cityCatalog;
    private final AccountRepository accountRepository;
    // Keep one domain customer per database id, shared by all of that customer's sessions.
    private final Map<Long, CustomerAccount> customers = new HashMap<>();
    private final Map<Long, GroceriesAccount> groceriesAccounts = new HashMap<>();

    public OrderController(CityCatalog cityCatalog, AccountRepository accountRepository) {
        this.cityCatalog = cityCatalog;
        this.accountRepository = accountRepository;
    }

    public synchronized Order placeOrder(AuthenticatedAccount account, String storeId, String destination,
                                         Map<String, Integer> products) throws SQLException {
        CustomerAccount customer = customerFor(account);
        if (storeId == null || storeId.isBlank()) {
            throw new IllegalArgumentException("Choose a grocery store.");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Enter a delivery address.");
        }
        if (products == null || products.isEmpty()) {
            throw new IllegalArgumentException("Add at least one product to your order.");
        }
        GroceryStore store = findStore(storeId);
        if (!store.isOpen()) {
            throw new IllegalStateException("This grocery store is closed.");
        }

        List<OrderLine> validatedLines = new ArrayList<>();
        for (String productId : products.keySet()) {
            Integer quantity = products.get(productId);
            if (productId == null || productId.isBlank() || quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("Each product needs an id and a positive whole-number quantity.");
            }
            Product product = findProduct(store, productId);
            if (product.isSoldOut()) {
                throw new IllegalStateException(product.getName() + " is sold out.");
            }
            validatedLines.add(new OrderLine(product, quantity));
        }

        Order order = new Order(store, destination.trim());
        for (OrderLine line : validatedLines) {
            order.addOrderLine(line.getProduct(), line.getQuantity());
        }
        order.placeOrder();
        customer.addOrder(order);
        return order;
    }

    public synchronized List<Order> customerOrders(AuthenticatedAccount account) throws SQLException {
        CustomerAccount customer = customerFor(account);
        return List.copyOf(customer.getOrders());
    }

    public synchronized void associateGroceriesAccount(String email, String storeId) throws SQLException {
        AccountCredentials stored = accountRepository.findByEmail(email);
        if (stored == null || !Role.GROCERIES.label().equals(stored.role())) {
            throw new IllegalArgumentException("Store associations require an existing groceries account: " + email);
        }
        GroceryStore store = findStore(storeId);
        GroceriesAccount groceries = new GroceriesAccount(stored.fullName(), stored.email(), stored.passwordHash(), stored.phone());
        groceries.setStore(store);
        groceriesAccounts.put(stored.id(), groceries);
    }

    public synchronized GroceryStore storeOrders(AuthenticatedAccount account) {
        return storeFor(account);
    }

    public synchronized GroceryStore acceptPendingOrder(AuthenticatedAccount account, int orderId) {
        GroceryStore store = storeFor(account);
        Order order = findPendingOrder(store, orderId);
        store.acceptPendingOrder(order);
        return store;
    }

    public synchronized GroceryStore readyNextOrder(AuthenticatedAccount account, int orderId) {
        GroceryStore store = storeFor(account);
        if (orderId <= 0) {
            throw new IllegalArgumentException("Choose a valid order id.");
        }
        store.readyNextOrder(orderId);
        return store;
    }

    public synchronized GroceryStore rejectPendingOrder(AuthenticatedAccount account, int orderId, String reason) {
        GroceryStore store = storeFor(account);
        Order order = findPendingOrder(store, orderId);
        store.rejectPendingOrder(order, reason);
        return store;
    }

    private GroceryStore findStore(String storeId) {
        for (GroceryStore store : cityCatalog.snapshot().groceryStores()) {
            if (store.getId().equals(storeId)) {
                return store;
            }
        }
        throw new IllegalArgumentException("The grocery store does not exist: " + storeId);
    }

    private Product findProduct(GroceryStore store, String productId) {
        for (Product product : store.getProducts()) {
            if (product.getId().equals(productId)) {
                return product;
            }
        }
        throw new IllegalArgumentException("A product does not belong to this grocery store.");
    }

    private Order findPendingOrder(GroceryStore store, int orderId) {
        if (orderId <= 0) {
            throw new IllegalArgumentException("Choose a valid order id.");
        }
        for (Order order : store.getPendingOrders()) {
            if (order.getOrderId() == orderId) {
                return order;
            }
        }
        throw new IllegalStateException("This order is no longer pending at your store.");
    }

    private GroceryStore storeFor(AuthenticatedAccount account) {
        if (account == null || account.role() != Role.GROCERIES) {
            throw new SecurityException("Only groceries accounts can manage store orders.");
        }
        GroceriesAccount groceries = groceriesAccounts.get(account.id());
        if (groceries == null) {
            throw new SecurityException("Your groceries account has no associated grocery store.");
        }
        return groceries.getStore();
    }

    private CustomerAccount customerFor(AuthenticatedAccount account) throws SQLException {
        if (account == null || account.role() != Role.CUSTOMER) {
            throw new SecurityException("Only customers can place or view their orders.");
        }
        CustomerAccount customer = customers.get(account.id());
        if (customer == null) {
            AccountCredentials stored = accountRepository.findByEmail(account.email());
            if (stored == null || stored.id() != account.id() || !Role.CUSTOMER.label().equals(stored.role())) {
                throw new SecurityException("The customer account is unavailable.");
            }
            customer = new CustomerAccount(stored.fullName(), stored.email(), stored.passwordHash(), stored.phone());
            customers.put(account.id(), customer);
        }
        return customer;
    }
}
