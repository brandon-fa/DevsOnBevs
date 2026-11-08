package com.littleisles.application.ord;

import com.littleisles.domain.ord.CitySnapshot;

public final class CityCatalog {
    private CitySnapshot snapshot;

    public CityCatalog(CitySnapshot snapshot) {
        replace(snapshot);
    }

    public synchronized CitySnapshot snapshot() {
        return snapshot;
    }

    /** Reset: swap in a freshly loaded city from the configuration file. */
    public synchronized void replace(CitySnapshot freshSnapshot) {
        if (freshSnapshot == null) {
            throw new IllegalArgumentException("The city catalog needs a loaded snapshot.");
        }
        this.snapshot = freshSnapshot;
    }
}