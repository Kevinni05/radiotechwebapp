package com.radiotech.radiotech_backend.security;

public final class GeoFencePolicy {

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoFencePolicy() {
    }

    public static double distanceMeters(double latitudeA, double longitudeA,
            double latitudeB, double longitudeB) {
        validateCoordinates(latitudeA, longitudeA);
        validateCoordinates(latitudeB, longitudeB);

        double latitudeDelta = Math.toRadians(latitudeB - latitudeA);
        double longitudeDelta = Math.toRadians(longitudeB - longitudeA);
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(latitudeA)) * Math.cos(Math.toRadians(latitudeB))
                        * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, haversine)));
    }

    public static boolean isWithinRadius(double latitudeA, double longitudeA,
            double latitudeB, double longitudeB, double radiusMeters) {
        if (!Double.isFinite(radiusMeters) || radiusMeters <= 0) {
            throw new IllegalArgumentException("Raggio geofence non valido.");
        }
        return distanceMeters(latitudeA, longitudeA, latitudeB, longitudeB) <= radiusMeters;
    }

    public static void validateAccuracy(double accuracyMeters) {
        validateAccuracy(accuracyMeters, 100);
    }

    public static void validateAccuracy(double accuracyMeters, double maximumAccuracyMeters) {
        if (!Double.isFinite(maximumAccuracyMeters) || maximumAccuracyMeters <= 0
                || !Double.isFinite(accuracyMeters) || accuracyMeters < 0
                || accuracyMeters > maximumAccuracyMeters) {
            throw new IllegalArgumentException("Accuratezza GPS non valida o insufficiente.");
        }
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Coordinate GPS non valide.");
        }
    }
}