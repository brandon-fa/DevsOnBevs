package com.littleisles.technicalservices.ord;

import com.littleisles.domain.ord.CityBounds;
import com.littleisles.domain.ord.CitySnapshot;
import com.littleisles.domain.ord.CourierPin;
import com.littleisles.domain.ord.CourierStatus;
import com.littleisles.domain.ord.CustomerPin;
import com.littleisles.domain.ord.MenuItem;
import com.littleisles.domain.ord.MenuItemStatus;
import com.littleisles.domain.ord.Restaurant;
import com.littleisles.domain.ord.RestaurantStatus;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * UC-ORD-01 Load Initial City
 * <p>
 * Loads the seeded city described by R-ORD-01 so ORD can publish a map read-model:
 * restaurants (name, status, lat/lon, menu), menu items (id, name, price, status),
 * couriers (id, lat/lon, status), and customers (id, name, delivery lat/lon).
 */
public final class CityConfigLoader {
    private static final Set<String> CITY_FIELDS = Set.of("minLat", "maxLat", "minLon", "maxLon");
    private static final Set<String> RESTAURANT_FIELDS = Set.of("name", "status", "lat", "lon");
    private static final Set<String> ITEM_FIELDS = Set.of("name", "priceCents", "status");
    private static final Set<String> COURIER_FIELDS = Set.of("lat", "lon", "status");
    private static final Set<String> CUSTOMER_FIELDS = Set.of("name", "lat", "lon");

    private final Path configFile;

    public CityConfigLoader(Path configFile) {
        this.configFile = configFile;
    }

    public CitySnapshot load() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        CityBounds bounds = readBounds(properties);
        List<Restaurant> restaurants = readRestaurants(properties);
        List<CourierPin> couriers = readCouriers(properties);
        List<CustomerPin> customers = readCustomers(properties);
        return new CitySnapshot(bounds, restaurants, couriers, customers);
    }

    public static Path locateConfigFile() {
        Path[] candidates = {
                Path.of("config", "initial-city.properties"),
                Path.of("..", "config", "initial-city.properties"),
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("Could not find config/initial-city.properties.");
    }

    private static CityBounds readBounds(Properties properties) {
        return new CityBounds(
                requiredDouble(properties, "city.minLat"),
                requiredDouble(properties, "city.maxLat"),
                requiredDouble(properties, "city.minLon"),
                requiredDouble(properties, "city.maxLon")
        );
    }

    private static List<Restaurant> readRestaurants(Properties properties) {
        Map<String, Map<String, String>> restaurants = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, String>>> menus = new LinkedHashMap<>();
        for (String key : sortedKeys(properties)) {
            if (!key.startsWith("restaurant.")) {
                continue;
            }
            String remainder = key.substring("restaurant.".length());
            String[] parts = remainder.split("\\.");
            if (parts.length == 2 && RESTAURANT_FIELDS.contains(parts[1])) {
                restaurants.computeIfAbsent(parts[0], ignored -> new LinkedHashMap<>())
                        .put(parts[1], required(properties, key));
                continue;
            }
            if (parts.length == 4 && parts[1].equals("item") && ITEM_FIELDS.contains(parts[3])) {
                menus.computeIfAbsent(parts[0], ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(parts[2], ignored -> new LinkedHashMap<>())
                        .put(parts[3], required(properties, key));
                continue;
            }
            throw new IllegalArgumentException("Unknown city setting: " + key);
        }

        List<Restaurant> loaded = new ArrayList<>();
        for (String id : restaurants.keySet()) {
            Map<String, String> fields = restaurants.get(id);
            requireFields(fields, RESTAURANT_FIELDS, "restaurant." + id);
            List<MenuItem> menu = new ArrayList<>();
            Map<String, Map<String, String>> items = menus.getOrDefault(id, Map.of());
            for (String itemId : items.keySet()) {
                Map<String, String> itemFields = items.get(itemId);
                requireFields(itemFields, ITEM_FIELDS, "restaurant." + id + ".item." + itemId);
                menu.add(new MenuItem(
                        itemId,
                        itemFields.get("name"),
                        requiredInt(itemFields.get("priceCents"), "restaurant." + id + ".item." + itemId + ".priceCents"),
                        MenuItemStatus.fromLabel(itemFields.get("status"))
                ));
            }
            loaded.add(new Restaurant(
                    id,
                    fields.get("name"),
                    RestaurantStatus.fromLabel(fields.get("status")),
                    requiredDoubleValue(fields.get("lat"), "restaurant." + id + ".lat"),
                    requiredDoubleValue(fields.get("lon"), "restaurant." + id + ".lon"),
                    menu
            ));
        }
        for (String restaurantId : menus.keySet()) {
            if (!restaurants.containsKey(restaurantId)) {
                throw new IllegalArgumentException("Menu items refer to an unknown restaurant: " + restaurantId);
            }
        }
        return loaded;
    }

    private static List<CourierPin> readCouriers(Properties properties) {
        Map<String, Map<String, String>> couriers = grouped(properties, "courier.", COURIER_FIELDS);
        List<CourierPin> loaded = new ArrayList<>();
        for (String id : couriers.keySet()) {
            Map<String, String> fields = couriers.get(id);
            requireFields(fields, COURIER_FIELDS, "courier." + id);
            loaded.add(new CourierPin(
                    id,
                    requiredDoubleValue(fields.get("lat"), "courier." + id + ".lat"),
                    requiredDoubleValue(fields.get("lon"), "courier." + id + ".lon"),
                    CourierStatus.fromLabel(fields.get("status"))
            ));
        }
        return loaded;
    }

    private static List<CustomerPin> readCustomers(Properties properties) {
        Map<String, Map<String, String>> customers = grouped(properties, "customer.", CUSTOMER_FIELDS);
        List<CustomerPin> loaded = new ArrayList<>();
        for (String id : customers.keySet()) {
            Map<String, String> fields = customers.get(id);
            requireFields(fields, CUSTOMER_FIELDS, "customer." + id);
            loaded.add(new CustomerPin(
                    id,
                    fields.get("name"),
                    requiredDoubleValue(fields.get("lat"), "customer." + id + ".lat"),
                    requiredDoubleValue(fields.get("lon"), "customer." + id + ".lon")
            ));
        }
        return loaded;
    }

    private static Map<String, Map<String, String>> grouped(Properties properties, String prefix, Set<String> fields) {
        Map<String, Map<String, String>> grouped = new LinkedHashMap<>();
        for (String key : sortedKeys(properties)) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String remainder = key.substring(prefix.length());
            int separator = remainder.indexOf('.');
            if (separator <= 0 || separator == remainder.length() - 1) {
                throw new IllegalArgumentException("Invalid city setting: " + key);
            }
            String id = remainder.substring(0, separator);
            String field = remainder.substring(separator + 1);
            if (!fields.contains(field)) {
                throw new IllegalArgumentException("Unknown city setting: " + key);
            }
            grouped.computeIfAbsent(id, ignored -> new LinkedHashMap<>()).put(field, required(properties, key));
        }
        return grouped;
    }

    private static void requireFields(Map<String, String> fields, Set<String> required, String prefix) {
        for (String field : required) {
            if (!fields.containsKey(field) || fields.get(field).isBlank()) {
                throw new IllegalArgumentException("Missing city setting: " + prefix + "." + field);
            }
        }
    }

    private static List<String> sortedKeys(Properties properties) {
        List<String> keys = new ArrayList<>(properties.stringPropertyNames());
        keys.sort(Comparator.naturalOrder());
        for (String key : keys) {
            if (!(key.startsWith("city.") || key.startsWith("restaurant.")
                    || key.startsWith("courier.") || key.startsWith("customer."))) {
                throw new IllegalArgumentException("Unexpected city setting: " + key);
            }
            if (key.startsWith("city.")) {
                String field = key.substring("city.".length());
                if (!CITY_FIELDS.contains(field)) {
                    throw new IllegalArgumentException("Unknown city setting: " + key);
                }
            }
        }
        return keys;
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing city setting: " + key);
        }
        return value.trim();
    }

    private static double requiredDouble(Properties properties, String key) {
        return requiredDoubleValue(required(properties, key), key);
    }

    private static double requiredDoubleValue(String value, String key) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("City setting must be a number: " + key);
        }
    }

    private static int requiredInt(String value, String key) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("City setting must be a whole number: " + key);
        }
    }
}
