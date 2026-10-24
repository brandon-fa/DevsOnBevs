package com.littleisles.technicalservices.ord;

import com.littleisles.domain.ord.CityBounds;
import com.littleisles.domain.ord.CitySnapshot;
import com.littleisles.domain.ord.CourierPin;
import com.littleisles.domain.ord.CourierStatus;
import com.littleisles.domain.ord.CustomerPin;
import com.littleisles.domain.ord.GroceryStore;
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
    void loadsGroceryStoresProductsCouriersAndCustomers() throws Exception {
        Path config = writeConfig(fullCity());

        CitySnapshot city = new CityConfigLoader(config).load();

        assertEquals(45.47, city.bounds().minLat());
        assertEquals(1, city.groceryStores().size());
        assertEquals("Olive & Rye", city.groceryStores().get(0).getName());
        assertTrue(city.groceryStores().get(0).isOpen());
        assertEquals(2, city.groceryStores().get(0).getProducts().size());
        assertEquals(6.95, city.groceryStores().get(0).getProducts().stream()
                .filter(product -> product.getId().equals("halloumi")).findFirst().orElseThrow().getPrice());
        assertTrue(city.groceryStores().get(0).getProducts().stream()
                .filter(product -> product.getId().equals("bread")).findFirst().orElseThrow().isSoldOut());
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
        assertTrue(city.groceryStores().isEmpty());
        assertTrue(city.couriers().isEmpty());
        assertTrue(city.customers().isEmpty());
    }

    @Test
    void cityBuilderCollectsMapEntitiesIntoUnmodifiableLists() {
        CitySnapshot city = CitySnapshot.builder(new CityBounds(45.47, 45.54, -73.64, -73.54))
                .addGroceryStore(new GroceryStore("market", "Market", 45.5, -73.6))
                .addCourier(new CourierPin("courier", 45.51, -73.59, CourierStatus.IDLE))
                .addCustomer(new CustomerPin("customer", "Customer", 45.52, -73.58))
                .build();

        assertEquals(1, city.groceryStores().size());
        assertEquals(1, city.couriers().size());
        assertEquals(1, city.customers().size());
        assertThrows(UnsupportedOperationException.class, () -> city.groceryStores().clear());
    }

    @Test
    void rejectsAGroceryStoreMissingCoordinates() throws Exception {
        Path config = writeConfig("""
                city.minLat=45.47
                city.maxLat=45.54
                city.minLon=-73.64
                city.maxLon=-73.54
                groceryStore.olive-rye.name=Olive & Rye
                groceryStore.olive-rye.open=true
                groceryStore.olive-rye.lat=45.52
                """);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new CityConfigLoader(config).load());
        assertTrue(error.getMessage().contains("groceryStore.olive-rye.lon"));
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
                groceryStore.olive-rye.name=Olive & Rye
                groceryStore.olive-rye.open=true
                groceryStore.olive-rye.lat=45.5234
                groceryStore.olive-rye.lon=-73.5801
                groceryStore.olive-rye.product.halloumi.name=Halloumi
                groceryStore.olive-rye.product.halloumi.priceCents=695
                groceryStore.olive-rye.product.halloumi.soldOut=false
                groceryStore.olive-rye.product.bread.name=Sesame bread
                groceryStore.olive-rye.product.bread.priceCents=425
                groceryStore.olive-rye.product.bread.soldOut=true
                courier.nadia.lat=45.5210
                courier.nadia.lon=-73.5850
                courier.nadia.status=idle
                customer.demo-customer.name=Demo Customer
                customer.demo-customer.lat=45.5158
                customer.demo-customer.lon=-73.5772
                """;
    }
}
