package com.littleisles.technicalservices.prc;

import com.littleisles.domain.prc.PricingConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingConfigLoaderTest {
    @TempDir
    Path tempDirectory;

    @Test
    void loadsEveryPricingValueFromConfiguration() throws Exception {
        Path file = tempDirectory.resolve("pricing.properties");
        Files.writeString(file, validConfiguration());

        PricingConfiguration configuration = new PricingConfigLoader(file).load();

        assertEquals(299, configuration.baseDeliveryFeeCents());
        assertEquals(50, configuration.deliveryRateCentsPerKilometre());
        assertEquals(150, configuration.serviceFeeCents());
        assertEquals(1_500, configuration.smallOrderThresholdCents());
        assertEquals(250, configuration.smallOrderSurchargeCents());
    }

    @Test
    void rejectsMissingOrUnknownPricingSettings() throws Exception {
        Path missing = tempDirectory.resolve("missing.properties");
        Files.writeString(missing, "pricing.delivery.baseFeeCents=299\n");
        assertThrows(IllegalArgumentException.class, () -> new PricingConfigLoader(missing).load());

        Path unknown = tempDirectory.resolve("unknown.properties");
        Files.writeString(unknown, validConfiguration() + "pricing.taxRate=15\n");
        assertThrows(IllegalArgumentException.class, () -> new PricingConfigLoader(unknown).load());
    }

    private static String validConfiguration() {
        return """
                pricing.delivery.baseFeeCents=299
                pricing.delivery.rateCentsPerKilometre=50
                pricing.serviceFeeCents=150
                pricing.smallOrder.thresholdCents=1500
                pricing.smallOrder.surchargeCents=250
                """;
    }
}
