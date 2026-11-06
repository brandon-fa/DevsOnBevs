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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves delivery addresses to real map coordinates.
 * Unknown / fake addresses are rejected so orders are not pinned to a hash spot.
 */
public final class AddressLocator {
    private static final Map<String, double[]> KNOWN_ADDRESSES = knownAddresses();
    private static final ConcurrentHashMap<String, double[]> GEOCODE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> NEGATIVE_CACHE = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();
    private static final Set<String> SKIP_TOKENS = Set.of(
            "rue", "avenue", "ave", "av", "avene", "boulevard", "boul", "blvd",
            "allee", "all", "chemin", "ch", "road", "rd", "street", "st",
            "des", "de", "du", "la", "le", "les", "montreal", "quebec", "canada",
            "brossard", "longueuil", "saint", "hubert");

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
        // Common typos seen in checkout.
        folded = folded.replace("avene", "avenue");
        return folded;
    }

    public static double[] locate(String address, CityBounds bounds) {
        double[] point = tryLocate(address, bounds);
        if (point == null) {
            throw new IllegalArgumentException(
                    "\"" + (address == null ? "" : address.trim())
                            + "\" is not a recognized Montreal-area street address. "
                            + "Enter a real address to place your order.");
        }
        return point;
    }

    /** Same as {@link #locate} but returns null instead of throwing when unresolved. */
    public static double[] tryLocate(String address, CityBounds bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("City bounds are required to place an address.");
        }
        String key = normalize(address);
        if (key.isEmpty() || houseNumber(key) == null || significantStreetTokens(key).isEmpty()) {
            return null;
        }

        double[] known = lookupKnown(key);
        if (known != null) {
            return new double[] {known[0], known[1]};
        }

        double[] cached = GEOCODE_CACHE.get(key);
        if (cached != null) {
            return new double[] {cached[0], cached[1]};
        }
        if (Boolean.TRUE.equals(NEGATIVE_CACHE.get(key))) {
            return null;
        }

        double[] geocoded = geocode(address, key);
        if (geocoded != null) {
            GEOCODE_CACHE.put(key, geocoded);
            return new double[] {geocoded[0], geocoded[1]};
        }
        NEGATIVE_CACHE.put(key, Boolean.TRUE);
        return null;
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
        Set<String> leftTokens = significantStreetTokens(left);
        Set<String> rightTokens = significantStreetTokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return false;
        return leftTokens.containsAll(rightTokens) || rightTokens.containsAll(leftTokens);
    }

    private static Set<String> significantStreetTokens(String key) {
        String street = key;
        String house = houseNumber(key);
        if (house != null) {
            street = key.substring(house.length()).trim();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : street.split("\\s+")) {
            if (part.length() < 3) continue;
            if (SKIP_TOKENS.contains(part)) continue;
            tokens.add(part);
        }
        return tokens;
    }

    private static double[] geocode(String original, String normalizedKey) {
        for (String query : searchQueries(original, normalizedKey)) {
            JsonArray results = searchNominatim(query);
            if (results == null) continue;
            for (JsonElement element : results) {
                if (!element.isJsonObject()) continue;
                JsonObject hit = element.getAsJsonObject();
                if (!hit.has("lat") || !hit.has("lon") || !hit.has("display_name")) continue;
                String display = normalize(hit.get("display_name").getAsString());
                if (!matchesRequestedStreet(normalizedKey, display)) continue;
                double lat = hit.get("lat").getAsDouble();
                double lon = hit.get("lon").getAsDouble();
                if (!Double.isFinite(lat) || !Double.isFinite(lon)) continue;
                return new double[] {lat, lon};
            }
        }
        return null;
    }

    private static List<String> searchQueries(String original, String key) {
        String trimmed = original == null ? "" : original.trim();
        List<String> queries = new ArrayList<>();
        LinkedHashSet<String> localities = new LinkedHashSet<>();
        Set<String> tokens = significantStreetTokens(key);
        if (tokens.contains("sorbiers")) {
            localities.add("Saint-Hubert, Quebec, Canada");
            localities.add("Longueuil, Quebec, Canada");
        }
        if (tokens.contains("auteuil") || (tokens.contains("grande") && key.contains("grande allee"))) {
            localities.add("Brossard, Quebec, Canada");
            localities.add("Longueuil, Quebec, Canada");
        }
        localities.add("Montreal, Quebec, Canada");
        localities.add("Quebec, Canada");

        queries.add(trimmed);
        for (String locality : localities) {
            queries.add(trimmed + ", " + locality);
        }
        return queries;
    }

    private static boolean matchesRequestedStreet(String key, String display) {
        Set<String> needed = significantStreetTokens(key);
        if (needed.isEmpty()) return false;
        for (String token : needed) {
            if (!display.contains(token)) return false;
        }
        return true;
    }

    private static JsonArray searchNominatim(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            URI uri = URI.create(
                    "https://nominatim.openstreetmap.org/search?format=json&limit=5&addressdetails=0&countrycodes=ca&q="
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
                return root.getAsJsonArray();
            }
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static Map<String, double[]> knownAddresses() {
        Map<String, double[]> addresses = new LinkedHashMap<>();
        // 6700 Avenue Auteuil, Brossard (South Shore)
        putAliases(addresses, new double[] {45.4681685, -73.4661549},
                "6700 avenue auteuil",
                "6700 auteuil",
                "6700 av auteuil",
                "6700 ave auteuil",
                "6700 avene auteuil");
        // 6700 Grande Allée, Brossard (South Shore)
        putAliases(addresses, new double[] {45.4631277, -73.4255244},
                "6700 grande allee",
                "6700 grande allée");
        // Allée des Sorbiers, Saint-Hubert / Longueuil (South Shore)
        putAliases(addresses, new double[] {45.4739591, -73.4186032},
                "4918 allee des sorbiers",
                "4918 all des sorbiers",
                "4918 des sorbiers",
                "4918 allee sorbiers");
        // Nearby house number on the same street (OSM has the street, not every civic number).
        putAliases(addresses, new double[] {45.4741076, -73.4182983},
                "4818 allee des sorbiers",
                "4818 all des sorbiers",
                "4818 des sorbiers",
                "4818 allee sorbiers");
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
