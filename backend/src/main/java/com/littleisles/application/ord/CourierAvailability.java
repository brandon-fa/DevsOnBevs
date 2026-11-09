package com.littleisles.application.ord;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CourierAvailability {
    @FunctionalInterface
    public interface Observer {
        void onCourierAvailable(long courierUserId) throws SQLException;
    }

    private final List<Observer> observers = new ArrayList<>();

    public synchronized void addObserver(Observer observer) {
        observers.add(Objects.requireNonNull(observer, "observer"));
    }

    public synchronized void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    public void notifyCourierAvailable(long courierUserId) throws SQLException {
        List<Observer> currentObservers;
        synchronized (this) {
            currentObservers = List.copyOf(observers);
        }
        for (Observer observer : currentObservers) {
            observer.onCourierAvailable(courierUserId);
        }
    }
}
