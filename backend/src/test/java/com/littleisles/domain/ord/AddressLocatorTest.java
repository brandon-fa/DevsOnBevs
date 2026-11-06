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
    void placesMisspelledAuteuilOnTheSouthShore() {
        double[] point = AddressLocator.locate("6700 avene auteuil", BOUNDS);

        assertEquals(45.4681685, point[0], 0.0001);
        assertEquals(-73.4661549, point[1], 0.0001);
    }

    @Test
    void placesSouthShoreAlleeDesSorbiersInSaintHubert() {
        double[] point = AddressLocator.locate("4918 Allée des Sorbiers", BOUNDS);

        assertEquals(45.4739591, point[0], 0.0001);
        assertEquals(-73.4186032, point[1], 0.0001);
        assertTrue(point[1] > -73.50, "Sorbiers must sit east of the St. Lawrence, on the South Shore");
    }

    @Test
    void places4818AlleeDesSorbiersOnTheSouthShoreNotMontrealIsland() {
        double[] point = AddressLocator.locate("4818 All. des Sorbiers", BOUNDS);

        assertEquals(45.4741076, point[0], 0.0001);
        assertEquals(-73.4182983, point[1], 0.0001);
        assertTrue(point[1] > -73.50, "4818 Sorbiers must sit on the South Shore");
        assertTrue(point[0] < 45.49, "4818 Sorbiers must not be hashed onto the island");
    }

    @Test
    void placesRueBaileInShaughnessyVillageNotAHashPoint() {
        double[] point = AddressLocator.locate("1819 rue baile", BOUNDS);

        assertEquals(45.4926516, point[0], 0.0001);
        assertEquals(-73.5781486, point[1], 0.0001);
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

    @Test
    void rejectsANonExistentAddress() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> AddressLocator.locate("9999 Rue DoesNotExistAnywhereXYZ", BOUNDS));

        assertTrue(error.getMessage().toLowerCase().contains("not a recognized"));
        assertNull(AddressLocator.tryLocate("9999 Rue DoesNotExistAnywhereXYZ", BOUNDS));
    }
}
