package com.littleisles.technicalservices.ord;

import com.littleisles.domain.ord.CitySnapshot;
import com.littleisles.domain.ord.CourierStatus;
import com.littleisles.domain.ord.MenuItemStatus;
import com.littleisles.domain.ord.RestaurantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CityConfigLoaderTest {
    @TempDir
    Path tempDirectory;

    @Test
    void loadsRestaurantsMenusCouriersAndCustomers() throws Exception {
        Path config = writeConfig(fullCity());

        CitySnapshot city = new CityConfigLoader(config).load();

        assertEquals(45.47, city.bounds().minLat());
        assertEquals(1, city.restaurants().size());
        assertEquals("Olive & Rye", city.restaurants().get(0).name());
        assertEquals(RestaurantStatus.OPEN, city.restaurants().get(0).status());
        assertEquals(2, city.restaurants().get(0).menu().size());
        assertEquals(695, city.restaurants().get(0).menu().stream()
                .filter(item -> item.id().equals("halloumi")).findFirst().orElseThrow().priceCents());
        assertEquals(MenuItemStatus.SOLD_OUT, city.restaurants().get(0).menu().stream()
                .filter(item -> item.id().equals("bread")).findFirst().orElseThrow().status());
        assertEquals(CourierStatus.IDLE, city.couriers().get(0).status());
        assertEquals("Demo Customer", city.customers().get(0).name());
        assertFalse(city.isEmpty());
        assertTrue(city.toJson().contains("\"halloumi\""));
    }

    @Test
    void allowsAnEmptyMapWhenOnlyBoundsArePresent() throws Exception {
        Path config = writeConfig("""
                city.minLat=45.47
                city.maxLat=45.54
                city.minLon=-73.64
                city.maxLon=-73.54
                """);

        CitySnapshot city = new CityConfigLoader(config).load();

        assertTrue(city.isEmpty());
        assertTrue(city.restaurants().isEmpty());
        assertTrue(city.couriers().isEmpty());
        assertTrue(city.customers().isEmpty());
    }

    @Test
    void rejectsARestaurantMissingCoordinates() throws Exception {
        Path config = writeConfig("""
                city.minLat=45.47
                city.maxLat=45.54
                city.minLon=-73.64
                city.maxLon=-73.54
                restaurant.olive-rye.name=Olive & Rye
                restaurant.olive-rye.status=open
                restaurant.olive-rye.lat=45.52
                """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new CityConfigLoader(config).load());
        assertTrue(error.getMessage().contains("restaurant.olive-rye.lon"));
    }

    private Path writeConfig(String contents) throws IOException {
        Path config = tempDirectory.resolve("initial-city.properties");
        Files.writeString(config, contents);
        return config;
    }

    private static String fullCity() {
        return """
                city.minLat=45.47
                city.maxLat=45.54
                city.minLon=-73.64
                city.maxLon=-73.54
                restaurant.olive-rye.name=Olive & Rye
                restaurant.olive-rye.status=open
                restaurant.olive-rye.lat=45.5234
                restaurant.olive-rye.lon=-73.5801
                restaurant.olive-rye.item.halloumi.name=Halloumi
                restaurant.olive-rye.item.halloumi.priceCents=695
                restaurant.olive-rye.item.halloumi.status=available
                restaurant.olive-rye.item.bread.name=Sesame bread
                restaurant.olive-rye.item.bread.priceCents=425
                restaurant.olive-rye.item.bread.status=sold_out
                courier.nadia.lat=45.5210
                courier.nadia.lon=-73.5850
                courier.nadia.status=idle
                customer.demo-customer.name=Demo Customer
                customer.demo-customer.lat=45.5158
                customer.demo-customer.lon=-73.5772
                """;
    }
}
