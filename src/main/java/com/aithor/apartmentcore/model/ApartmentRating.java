package com.aithor.apartmentcore.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ApartmentRating {
    public double totalRating = 0;
    public int ratingCount = 0;
    public Map<UUID, Double> raters = new HashMap<>();

    public double getAverageRating() {
        return ratingCount > 0 ? totalRating / ratingCount : 0.0;
    }
}