package com.littleisles.domain.ord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddressLocatorTest {
    private static final CityBounds BOUNDS = new CityBounds(45.45, 45.56, -73.68, -73.40);

    @Test
    void placesSouthShoreAvenueAuteuilInBrossard() {
        double[] point = AddressLocator.locate("6700 Avenue Auteuil", BOUNDS);

        assertEquals(45.4681685, point[0], 0.0001);
        assertEquals(-73.4661549, point[1], 0.0001);
        assertTrue(point[1] > -73.50, "Auteuil must sit east of the St. Lawrence, on the South Shore");
    }

    @Test
    void placesSouthShoreAlleeDesSorbiersInSaintHubert() {
        double[] point = AddressLocator.locate("4918 Allée des Sorbiers", BOUNDS);

        assertEquals(45.4739591, point[0], 0.0001);
        assertEquals(-73.4186032, point[1], 0.0001);
        assertTrue(point[1] > -73.50, "Sorbiers must sit east of the St. Lawrence, on the South Shore");
    }

    @Test
    void placesRueBaileInShaughnessyVillageNotAHashPoint() {
        double[] point = AddressLocator.locate("1819 rue baile", BOUNDS);

        assertEquals(45.4926516, point[0], 0.0001);
        assertEquals(-73.5781486, point[1], 0.0001);
        assertTrue(point[0] < 45.51, "Rue Baile is south of downtown core hash leftovers");
        assertTrue(point[1] < -73.57, "Rue Baile sits west of Saint-Laurent");
    }

    @Test
    void placesGrandeAlleeOnTheSouthShore() {
        double[] point = AddressLocator.locate("6700 Grande Allée", BOUNDS);

        assertEquals(45.4631277, point[0], 0.0001);
        assertEquals(-73.4255244, point[1], 0.0001);
        assertTrue(point[1] > -73.50);
    }

    @Test
    void normalizesAccentedStreetNamesForLookup() {
        double[] withAccent = AddressLocator.locate("4918 Allée des Sorbiers", BOUNDS);
        double[] withoutAccent = AddressLocator.locate("4918 Allee des Sorbiers", BOUNDS);
        double[] abbreviated = AddressLocator.locate("4918 All. des Sorbiers", BOUNDS);

        assertArrayEquals(withAccent, withoutAccent);
        assertArrayEquals(withAccent, abbreviated);
    }
}
