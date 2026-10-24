package com.littleisles.technicalservices.ord;

import com.littleisles.domain.ord.CityBounds;
import com.littleisles.domain.ord.CitySnapshot;
import com.littleisles.domain.ord.CourierPin;
import com.littleisles.domain.ord.CourierStatus;
import com.littleisles.domain.ord.CustomerPin;
import com.littleisles.domain.ord.GroceryStore;
import com.littleisles.domain.ord.Product;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

public final class CityConfigLoader {
    private static final Set<String> CITY_FIELDS = Set.of("minLat", "maxLat", "minLon", "maxLon");
    private static final Set<String> STORE_FIELDS = Set.of("name", "open", "lat", "lon");
    private static final Set<String> PRODUCT_FIELDS = Set.of("name", "priceCents", "soldOut");
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
        validateKeys(properties);

        CityBounds bounds = new CityBounds(
                readDouble(properties, "city.minLat"),
                readDouble(properties, "city.maxLat"),
                readDouble(properties, "city.minLon"),
                readDouble(properties, "city.maxLon"));
        CitySnapshot.Builder city = CitySnapshot.builder(bounds);

        for (String id : findIds(properties, "groceryStore.")) {
            city.addGroceryStore(readStore(properties, id));
        }
        for (String id : findIds(properties, "courier.")) {
            String prefix = "courier." + id + ".";
            city.addCourier(new CourierPin(id,
                    readDouble(properties, prefix + "lat"),
                    readDouble(properties, prefix + "lon"),
                    CourierStatus.fromLabel(required(properties, prefix + "status"))));
        }
        for (String id : findIds(properties, "customer.")) {
            String prefix = "customer." + id + ".";
            city.addCustomer(new CustomerPin(id,
                    required(properties, prefix + "name"),
                    readDouble(properties, prefix + "lat"),
                    readDouble(properties, prefix + "lon")));
        }
        return city.build();
    }

    private static GroceryStore readStore(Properties properties, String id) {
        String prefix = "groceryStore." + id + ".";
        GroceryStore store = new GroceryStore(id,
                required(properties, prefix + "name"),
                readDouble(properties, prefix + "lat"),
                readDouble(properties, prefix + "lon"));
        store.setOpen(readBoolean(properties, prefix + "open"));

        for (String productId : findIds(properties, prefix + "product.")) {
            String productPrefix = prefix + "product." + productId + ".";
            // The configuration uses cents; Product stores its price in dollars.
            double price = readInt(properties, productPrefix + "priceCents") / 100.0;
            Product product = new Product(productId, required(properties, productPrefix + "name"), price);
            product.setSoldOut(readBoolean(properties, productPrefix + "soldOut"));
            store.addProduct(product);
        }
        return store;
    }

    private static Set<String> findIds(Properties properties, String prefix) {
        // A sorted set gives the same loading order on every startup.
        Set<String> ids = new TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                String remainder = key.substring(prefix.length());
                int separator = remainder.indexOf('.');
                ids.add(remainder.substring(0, separator));
            }
        }
        return ids;
    }

    private static void validateKeys(Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            String[] parts = key.split("\\.", -1);
            boolean valid = false;

            if (parts.length == 2 && parts[0].equals("city")) {
                valid = CITY_FIELDS.contains(parts[1]);
            } else if (parts.length == 3 && !parts[1].isBlank()) {
                if (parts[0].equals("groceryStore")) {
                    valid = STORE_FIELDS.contains(parts[2]);
                } else if (parts[0].equals("courier")) {
                    valid = COURIER_FIELDS.contains(parts[2]);
                } else if (parts[0].equals("customer")) {
                    valid = CUSTOMER_FIELDS.contains(parts[2]);
                }
            } else if (parts.length == 5 && parts[0].equals("groceryStore") && parts[2].equals("product")) {
                valid = !parts[1].isBlank() && !parts[3].isBlank() && PRODUCT_FIELDS.contains(parts[4]);
            }

            if (!valid) {
                throw new IllegalArgumentException("Unknown city setting: " + key);
            }
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing city setting: " + key);
        }
        return value.trim();
    }

    private static double readDouble(Properties properties, String key) {
        String value = required(properties, key);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("City setting must be a number: " + key);
        }
    }

    private static int readInt(Properties properties, String key) {
        String value = required(properties, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("City setting must be a whole number: " + key);
        }
    }

    private static boolean readBoolean(Properties properties, String key) {
        String value = required(properties, key);
        if (value.equals("true")) {
            return true;
        }
        if (value.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException("City setting must be true or false: " + key);
    }

    public static Path locateConfigFile() {
        Path[] candidates = {
                Path.of("config", "initial-city.properties"),
                Path.of("..", "config", "initial-city.properties")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("Could not find config/initial-city.properties.");
    }
}
