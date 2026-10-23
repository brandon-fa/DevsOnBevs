package com.littleisles.domain.ord;

public record CityBounds(double minLat, double maxLat, double minLon, double maxLon) {
    public CityBounds {
        if (!(minLat < maxLat) || !(minLon < maxLon)) {
            throw new IllegalArgumentException("City bounds must have minLat < maxLat and minLon < maxLon.");
        }
    }
}
