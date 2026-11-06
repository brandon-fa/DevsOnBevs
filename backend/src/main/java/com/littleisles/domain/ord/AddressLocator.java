package com.littleisles.domain.ord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves delivery addresses to map coordinates.
 * Prefers a local known-address table, then OpenStreetMap Nominatim, so pins
 * land on the real street instead of a hashed placeholder inside the city box.
 */
public final class AddressLocator {
    private static final Map<String, double[]> KNOWN_ADDRESSES = knownAddresses();
    private static final ConcurrentHashMap<String, double[]> GEOCODE_CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private AddressLocator() {
    }

    public static String normalize(String address) {
        if (address == null) return "";
        String folded = Normalizer.normalize(address.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace(',', ' ')
                .replace('.', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        return folded;
    }

    public static double[] locate(String address, CityBounds bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("City bounds are required to place an address.");
        }
        String key = normalize(address);
        if (key.isEmpty()) {
            return new double[] {
                    (bounds.minLat() + bounds.maxLat()) / 2.0,
                    (bounds.minLon() + bounds.maxLon()) / 2.0
            };
        }

        double[] known = lookupKnown(key);
        if (known != null) {
            return clamp(known[0], known[1], bounds);
        }

        double[] cached = GEOCODE_CACHE.get(key);
        if (cached != null) {
            return clamp(cached[0], cached[1], bounds);
        }

        double[] geocoded = geocode(address);
        if (geocoded != null) {
            GEOCODE_CACHE.put(key, geocoded);
            return clamp(geocoded[0], geocoded[1], bounds);
        }

        throw new IllegalArgumentException(
                "Could not place \"" + address.trim() + "\" on the map. Enter a real Montreal-area street address.");
    }

    private static double[] lookupKnown(String key) {
        double[] exact = KNOWN_ADDRESSES.get(key);
        if (exact != null) return exact;
        String house = houseNumber(key);
        if (house == null) return null;
        for (Map.Entry<String, double[]> entry : KNOWN_ADDRESSES.entrySet()) {
            if (!house.equals(houseNumber(entry.getKey()))) continue;
            if (streetTokensMatch(key, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String houseNumber(String key) {
        int space = key.indexOf(' ');
        if (space <= 0) return null;
        String first = key.substring(0, space);
        return first.matches("\\d+[a-z]?") ? first : null;
    }

    private static boolean streetTokensMatch(String left, String right) {
        String leftStreet = stripHouseNumber(left);
        String rightStreet = stripHouseNumber(right);
        if (leftStreet.isEmpty() || rightStreet.isEmpty()) return false;
        if (leftStreet.equals(rightStreet)) return true;
        return leftStreet.contains(rightStreet) || rightStreet.contains(leftStreet);
    }

    private static String stripHouseNumber(String key) {
        String house = houseNumber(key);
        if (house == null) return key;
        return key.substring(house.length()).trim()
                .replaceAll("\\b(rue|avenue|ave|av|boulevard|boul|blvd|allee|chemin|ch|road|rd|street|st)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static double[] geocode(String address) {
        String query = address.trim();
        String lowered = query.toLowerCase(Locale.ROOT);
        if (!lowered.contains("montreal") && !lowered.contains("montréal")
                && !lowered.contains("quebec") && !lowered.contains("québec")
                && !lowered.contains("brossard") && !lowered.contains("longueuil")
                && !lowered.contains("saint-hubert") && !lowered.contains("st-hubert")) {
            query = query + ", Montreal, Quebec, Canada";
        }
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = URI.create(
                    "https://nominatim.openstreetmap.org/search?format=json&limit=1&addressdetails=0&countrycodes=ca&q="
                            + encoded);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "LittleIsles/1.0 (SOEN343 Concordia; local demo)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(response.body(), StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonArray()) return null;
                JsonArray results = root.getAsJsonArray();
                if (results.isEmpty()) return null;
                JsonObject first = results.get(0).getAsJsonObject();
                double lat = first.get("lat").getAsDouble();
                double lon = first.get("lon").getAsDouble();
                if (!Double.isFinite(lat) || !Double.isFinite(lon)) return null;
                return new double[] {lat, lon};
            }
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static double[] clamp(double lat, double lon, CityBounds bounds) {
        // Keep the real point when it already sits in the configured city box.
        if (lat >= bounds.minLat() && lat <= bounds.maxLat()
                && lon >= bounds.minLon() && lon <= bounds.maxLon()) {
            return new double[] {lat, lon};
        }
        // Still return the real coordinates so the pin is correct even if slightly
        // outside the seeded demo bounds; the map can still show it.
        return new double[] {lat, lon};
    }

    private static Map<String, double[]> knownAddresses() {
        Map<String, double[]> addresses = new LinkedHashMap<>();
        // 6700 Avenue Auteuil, Brossard (South Shore)
        putAliases(addresses, new double[] {45.4681685, -73.4661549},
                "6700 avenue auteuil",
                "6700 auteuil",
                "6700 av auteuil",
                "6700 ave auteuil");
        // 6700 Grande Allée, Brossard (South Shore)
        putAliases(addresses, new double[] {45.4631277, -73.4255244},
                "6700 grande allee",
                "6700 grande allée");
        // 4918 Allée des Sorbiers, Saint-Hubert / Longueuil (South Shore)
        putAliases(addresses, new double[] {45.4739591, -73.4186032},
                "4918 allee des sorbiers",
                "4918 all des sorbiers",
                "4918 des sorbiers",
                "4918 allee sorbiers");
        // 1819 Rue Baile, Ville-Marie / Shaughnessy Village
        putAliases(addresses, new double[] {45.4926516, -73.5781486},
                "1819 rue baile",
                "1819 baile");
        // 4289 Rue Berri, Plateau-Mont-Royal
        putAliases(addresses, new double[] {45.5236417, -73.5787413},
                "4289 rue berri",
                "4289 berri");
        return Map.copyOf(addresses);
    }

    private static void putAliases(Map<String, double[]> addresses, double[] point, String... aliases) {
        for (String alias : aliases) {
            addresses.put(normalize(alias), point);
        }
    }
}
