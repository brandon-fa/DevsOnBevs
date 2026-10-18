package com.littleisles.application.ord;

import com.littleisles.domain.ord.CitySnapshot;

public final class CityCatalog {
    private final CitySnapshot snapshot;

    public CityCatalog(CitySnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("The city catalog needs a loaded snapshot.");
        }
        this.snapshot = snapshot;
    }

    public CitySnapshot snapshot() {
        return snapshot;
    }
}
