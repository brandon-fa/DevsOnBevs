package com.littleisles.technicalservices.prc;

import com.littleisles.domain.prc.PricingConfiguration;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

public final class PricingConfigLoader {
    private static final String BASE_FEE = "pricing.delivery.baseFeeCents";
    private static final String RATE = "pricing.delivery.rateCentsPerKilometre";
    private static final String SERVICE_FEE = "pricing.serviceFeeCents";
    private static final String SMALL_ORDER_THRESHOLD = "pricing.smallOrder.thresholdCents";
    private static final String SMALL_ORDER_SURCHARGE = "pricing.smallOrder.surchargeCents";
    private static final Set<String> EXPECTED_KEYS = Set.of(
            BASE_FEE, RATE, SERVICE_FEE, SMALL_ORDER_THRESHOLD, SMALL_ORDER_SURCHARGE);

    private final Path configFile;

    public PricingConfigLoader(Path configFile) {
        this.configFile = configFile;
    }

    public PricingConfiguration load() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        for (String key : properties.stringPropertyNames()) {
            if (!EXPECTED_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown pricing setting: " + key);
            }
        }
        return new PricingConfiguration(
                readCents(properties, BASE_FEE),
                readCents(properties, RATE),
                readCents(properties, SERVICE_FEE),
                readCents(properties, SMALL_ORDER_THRESHOLD),
                readCents(properties, SMALL_ORDER_SURCHARGE));
    }

    private static long readCents(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing pricing setting: " + key);
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Pricing setting must be a whole number of cents: " + key);
        }
    }

    public static Path locateConfigFile() {
        Path[] candidates = {
                Path.of("config", "pricing.properties"),
                Path.of("..", "config", "pricing.properties")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("Could not find config/pricing.properties.");
    }
}
