package com.littleisles.domain.ord;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public record CitySnapshot(
        CityBounds bounds,
        List<Restaurant> restaurants,
        List<CourierPin> couriers,
        List<CustomerPin> customers
) {
    public CitySnapshot {
        if (bounds == null) {
            throw new IllegalArgumentException("The city needs display bounds.");
        }
        restaurants = List.copyOf(restaurants == null ? List.of() : restaurants);
        couriers = List.copyOf(couriers == null ? List.of() : couriers);
        customers = List.copyOf(customers == null ? List.of() : customers);
    }

    public boolean isEmpty() {
        return restaurants.isEmpty() && couriers.isEmpty() && customers.isEmpty();
    }

    public static Builder builder(CityBounds bounds) {
        return new Builder(bounds);
    }

    public static final class Builder {
        private final CityBounds bounds;
        private final List<Restaurant> restaurants = new ArrayList<>();
        private final List<CourierPin> couriers = new ArrayList<>();
        private final List<CustomerPin> customers = new ArrayList<>();

        private Builder(CityBounds bounds) {
            if (bounds == null) {
                throw new IllegalArgumentException("The city needs display bounds.");
            }
            this.bounds = bounds;
        }

        public Builder addRestaurant(Restaurant restaurant) {
            restaurants.add(requireValue(restaurant, "A city restaurant cannot be null."));
            return this;
        }

        public Builder addCourier(CourierPin courier) {
            couriers.add(requireValue(courier, "A city courier cannot be null."));
            return this;
        }

        public Builder addCustomer(CustomerPin customer) {
            customers.add(requireValue(customer, "A city customer cannot be null."));
            return this;
        }

        public CitySnapshot build() {
            return new CitySnapshot(bounds, restaurants, couriers, customers);
        }

        private static <T> T requireValue(T value, String message) {
            if (value == null) {
                throw new IllegalArgumentException(message);
            }
            return value;
        }
    }

    public String toJson() {
        StringJoiner restaurantJson = new StringJoiner(",");
        for (Restaurant restaurant : restaurants) {
            StringJoiner menuJson = new StringJoiner(",");
            for (MenuItem item : restaurant.menu()) {
                menuJson.add("{"
                        + "\"id\":" + quote(item.id())
                        + ",\"name\":" + quote(item.name())
                        + ",\"priceCents\":" + item.priceCents()
                        + ",\"status\":" + quote(item.status().label())
                        + "}");
            }
            restaurantJson.add("{"
                    + "\"id\":" + quote(restaurant.id())
                    + ",\"name\":" + quote(restaurant.name())
                    + ",\"status\":" + quote(restaurant.status().label())
                    + ",\"lat\":" + restaurant.lat()
                    + ",\"lon\":" + restaurant.lon()
                    + ",\"menu\":[" + menuJson + "]"
                    + "}");
        }
        StringJoiner courierJson = new StringJoiner(",");
        for (CourierPin courier : couriers) {
            courierJson.add("{"
                    + "\"id\":" + quote(courier.id())
                    + ",\"lat\":" + courier.lat()
                    + ",\"lon\":" + courier.lon()
                    + ",\"status\":" + quote(courier.status().label())
                    + "}");
        }
        StringJoiner customerJson = new StringJoiner(",");
        for (CustomerPin customer : customers) {
            customerJson.add("{"
                    + "\"id\":" + quote(customer.id())
                    + ",\"name\":" + quote(customer.name())
                    + ",\"lat\":" + customer.lat()
                    + ",\"lon\":" + customer.lon()
                    + "}");
        }
        return "{"
                + "\"bounds\":{"
                + "\"minLat\":" + bounds.minLat()
                + ",\"maxLat\":" + bounds.maxLat()
                + ",\"minLon\":" + bounds.minLon()
                + ",\"maxLon\":" + bounds.maxLon()
                + "}"
                + ",\"restaurants\":[" + restaurantJson + "]"
                + ",\"couriers\":[" + courierJson + "]"
                + ",\"customers\":[" + customerJson + "]"
                + "}";
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                + "\"";
    }
}
