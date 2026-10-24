package com.littleisles.domain.ord;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public record CitySnapshot(
        CityBounds bounds,
        List<GroceryStore> groceryStores,
        List<CourierPin> couriers,
        List<CustomerPin> customers
) {
    public CitySnapshot {
        if (bounds == null) {
            throw new IllegalArgumentException("The city needs display bounds.");
        }
        if (groceryStores == null) groceryStores = List.of();
        if (couriers == null) couriers = List.of();
        if (customers == null) customers = List.of();
        groceryStores = List.copyOf(groceryStores);
        couriers = List.copyOf(couriers);
        customers = List.copyOf(customers);
    }

    public boolean isEmpty() {
        return groceryStores.isEmpty() && couriers.isEmpty() && customers.isEmpty();
    }

    public static Builder builder(CityBounds bounds) {
        return new Builder(bounds);
    }

    public static final class Builder {
        private final CityBounds bounds;
        private final List<GroceryStore> groceryStores = new ArrayList<>();
        private final List<CourierPin> couriers = new ArrayList<>();
        private final List<CustomerPin> customers = new ArrayList<>();

        private Builder(CityBounds bounds) {
            if (bounds == null) {
                throw new IllegalArgumentException("The city needs display bounds.");
            }
            this.bounds = bounds;
        }

        public Builder addGroceryStore(GroceryStore groceryStore) {
            if (groceryStore == null) {
                throw new IllegalArgumentException("A city grocery store cannot be null.");
            }
            groceryStores.add(groceryStore);
            return this;
        }

        public Builder addCourier(CourierPin courier) {
            if (courier == null) {
                throw new IllegalArgumentException("A city courier cannot be null.");
            }
            couriers.add(courier);
            return this;
        }

        public Builder addCustomer(CustomerPin customer) {
            if (customer == null) {
                throw new IllegalArgumentException("A city customer cannot be null.");
            }
            customers.add(customer);
            return this;
        }

        public CitySnapshot build() {
            return new CitySnapshot(bounds, groceryStores, couriers, customers);
        }

    }

    public String toJson() {
        JsonObject city = new JsonObject();
        JsonObject boundsJson = new JsonObject();
        boundsJson.addProperty("minLat", bounds.minLat());
        boundsJson.addProperty("maxLat", bounds.maxLat());
        boundsJson.addProperty("minLon", bounds.minLon());
        boundsJson.addProperty("maxLon", bounds.maxLon());
        city.add("bounds", boundsJson);

        JsonArray storesJson = new JsonArray();
        for (GroceryStore store : groceryStores) {
            JsonObject storeJson = new JsonObject();
            storeJson.addProperty("id", store.getId());
            storeJson.addProperty("name", store.getName());
            storeJson.addProperty("open", store.isOpen());
            storeJson.addProperty("lat", store.getLat());
            storeJson.addProperty("lon", store.getLon());

            JsonArray productsJson = new JsonArray();
            for (Product product : store.getProducts()) {
                JsonObject productJson = new JsonObject();
                productJson.addProperty("id", product.getId());
                productJson.addProperty("name", product.getName());
                productJson.addProperty("priceCents", Math.round(product.getPrice() * 100));
                productJson.addProperty("soldOut", product.isSoldOut());
                productsJson.add(productJson);
            }
            storeJson.add("products", productsJson);
            storesJson.add(storeJson);
        }
        city.add("groceryStores", storesJson);

        JsonArray couriersJson = new JsonArray();
        for (CourierPin courier : couriers) {
            JsonObject courierJson = new JsonObject();
            courierJson.addProperty("id", courier.id());
            courierJson.addProperty("lat", courier.lat());
            courierJson.addProperty("lon", courier.lon());
            courierJson.addProperty("status", courier.status().label());
            couriersJson.add(courierJson);
        }
        city.add("couriers", couriersJson);

        JsonArray customersJson = new JsonArray();
        for (CustomerPin customer : customers) {
            JsonObject customerJson = new JsonObject();
            customerJson.addProperty("id", customer.id());
            customerJson.addProperty("name", customer.name());
            customerJson.addProperty("lat", customer.lat());
            customerJson.addProperty("lon", customer.lon());
            customersJson.add(customerJson);
        }
        city.add("customers", customersJson);
        return new Gson().toJson(city);
    }
}
