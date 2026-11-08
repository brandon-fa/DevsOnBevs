package com.littleisles.domain.prc;

public final class HaversineDistance {
    private static final double EARTH_RADIUS_KILOMETRES = 6_371.0088;

    private HaversineDistance() {
    }

    public static double kilometresBetween(GeoPoint first, GeoPoint second) {
        double latitudeDelta = Math.toRadians(second.latitude() - first.latitude());
        double longitudeDelta = Math.toRadians(second.longitude() - first.longitude());
        double firstLatitude = Math.toRadians(first.latitude());
        double secondLatitude = Math.toRadians(second.latitude());

        double sineLatitude = Math.sin(latitudeDelta / 2.0);
        double sineLongitude = Math.sin(longitudeDelta / 2.0);
        double haversine = sineLatitude * sineLatitude
                + Math.cos(firstLatitude) * Math.cos(secondLatitude)
                * sineLongitude * sineLongitude;
        double boundedHaversine = Math.max(0.0, Math.min(1.0, haversine));
        return 2.0 * EARTH_RADIUS_KILOMETRES * Math.asin(Math.sqrt(boundedHaversine));
    }
}
