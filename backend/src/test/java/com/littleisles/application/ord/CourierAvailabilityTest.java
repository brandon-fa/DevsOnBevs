package com.littleisles.application.ord;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourierAvailabilityTest {
    @Test
    void notifiesObserversWhenCourierBecomesAvailable() throws Exception {
        CourierAvailability availability = new CourierAvailability();
        AtomicLong availableCourierId = new AtomicLong();
        availability.addObserver(availableCourierId::set);

        availability.notifyCourierAvailable(42);

        assertEquals(42, availableCourierId.get());
    }

    @Test
    void stopsNotifyingAnObserverAfterRemoval() throws Exception {
        CourierAvailability availability = new CourierAvailability();
        AtomicLong availableCourierId = new AtomicLong();
        CourierAvailability.Observer observer = availableCourierId::set;
        availability.addObserver(observer);
        availability.removeObserver(observer);

        availability.notifyCourierAvailable(42);

        assertEquals(0, availableCourierId.get());
    }
}
