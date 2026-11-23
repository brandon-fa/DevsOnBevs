package com.littleisles.application.ntf;

import com.littleisles.application.ord.OrderStatusListener;
import com.littleisles.domain.ord.Order;
import com.littleisles.domain.ord.TransitionEvent;
import com.littleisles.domain.reg.AuthenticatedAccount;
import com.littleisles.domain.reg.Role;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * NTF: short in-memory notification list per user, shown inline on the dashboard.
 * Plugs into OrderController only through the {@link OrderStatusListener} extension point:
 * register it once at startup with {@code orderController.addStatusListener(center)}.
 */
public final class NotificationCenter implements OrderStatusListener {
    private static final int MAX_PER_USER = 10;
    private static final String THRESHOLD_KEY = "ntf.dispatchQueue.thresholdSeconds";
    private static final Duration DEFAULT_THRESHOLD = Duration.ofSeconds(60);

    public record Notification(long id, String message, String createdAt) {
    }

    /** A ready order no courier has accepted yet, as last announced to this module. */
    private record AwaitingCourier(int orderId, String storeName, Instant readyAt) {
    }

    private final Map<Long, Deque<Notification>> byUser = new HashMap<>();
    private final Map<Integer, AwaitingCourier> dispatchQueue = new LinkedHashMap<>();
    private final Set<String> alerted = new HashSet<>();
    private final Duration dispatchThreshold;
    private long nextId = 1;

    public NotificationCenter() {
        this(loadThreshold());
    }

    public NotificationCenter(Duration dispatchThreshold) {
        this.dispatchThreshold = dispatchThreshold;
    }

    /** Extension point callback: OrderController saved {@code status} for {@code order}. */
    @Override
    public synchronized void orderStatusChanged(Order order, String status) {
        trackDispatchQueue(order, status);
        String what = customerMessage(order, status);
        if (what != null) {
            notifyUser(order.getCustomerUserId(), "Order #" + order.getOrderId() + " " + what);
        }
    }

    /** NTF query: the account's notifications, newest first. Operators also get dispatch-queue wait alerts. */
    public synchronized List<Notification> forAccount(AuthenticatedAccount account) {
        if (account == null) {
            throw new SecurityException("Log in to continue.");
        }
        if (account.role() == Role.OPERATOR) {
            alertLongDispatchWaits(account.id());
        }
        return List.copyOf(byUser.getOrDefault(account.id(), new ArrayDeque<>()));
    }

    /** Reset: forget every notification, alert and tracked order. */
    public synchronized void clear() {
        byUser.clear();
        dispatchQueue.clear();
        alerted.clear();
    }

    /** R-NTF-01: what to tell the order's customer for each saved status (null = say nothing). */
    private static String customerMessage(Order order, String status) {
        String store = order.getStore().getName();
        return switch (status) {
            case "placed" -> "was placed at " + store + ".";
            case "preparing" -> "was accepted by " + store + " and is being prepared.";
            case "ready" -> "is ready and waiting for a courier.";
            case "accepted" -> "has been assigned a courier.";
            case "picked_up" -> "was picked up and is on its way.";
            case "delivered" -> "was delivered.";
            case "canceled" -> "was rejected by " + store + ". Reason: " + order.getRejectionReason();
            case "refunded" -> "was refunded.";
            default -> null;
        };
    }

    /** R-NTF-02: an order waits for a courier from "ready" until any later status (courier accepted, ...). */
    private void trackDispatchQueue(Order order, String status) {
        if ("ready".equals(status)) {
            dispatchQueue.put(order.getOrderId(),
                    new AwaitingCourier(order.getOrderId(), order.getStore().getName(), readyAt(order)));
        } else {
            dispatchQueue.remove(order.getOrderId());
        }
    }

    /** R-NTF-02: alert this operator once for each order that has waited too long for a courier. */
    private void alertLongDispatchWaits(long operatorId) {
        Instant cutoff = Instant.now().minus(dispatchThreshold);
        for (AwaitingCourier waiting : dispatchQueue.values()) {
            if (waiting.readyAt().isBefore(cutoff) && alerted.add(operatorId + ":" + waiting.orderId())) {
                notifyUser(operatorId, "Order #" + waiting.orderId() + " at " + waiting.storeName()
                        + " has waited over " + dispatchThreshold.toSeconds() + "s in the dispatch queue.");
            }
        }
    }

    private void notifyUser(Long userId, String message) {
        if (userId == null) {
            return;
        }
        Deque<Notification> list = byUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        list.addFirst(new Notification(nextId++, message, Instant.now().toString()));
        while (list.size() > MAX_PER_USER) {
            list.removeLast();
        }
    }

    private static Instant readyAt(Order order) {
        List<TransitionEvent> events = order.getTransitionEvents();
        for (int index = events.size() - 1; index >= 0; index--) {
            if ("ready".equals(events.get(index).getToState())) {
                return Instant.parse(events.get(index).getTimestamp());
            }
        }
        return Instant.now();
    }

    private static Duration loadThreshold() {
        for (Path file : List.of(Path.of("config", "notifications.properties"),
                Path.of("..", "config", "notifications.properties"))) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try (Reader reader = Files.newBufferedReader(file)) {
                Properties properties = new Properties();
                properties.load(reader);
                return Duration.ofSeconds(Long.parseLong(properties.getProperty(THRESHOLD_KEY).trim()));
            } catch (IOException | RuntimeException exception) {
                return DEFAULT_THRESHOLD;
            }
        }
        return DEFAULT_THRESHOLD;
    }
}
