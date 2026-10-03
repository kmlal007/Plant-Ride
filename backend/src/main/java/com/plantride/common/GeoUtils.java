package com.plantride.common;

public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    /** Plant paths are not straight lines; this factor approximates real walking/driving distance. */
    public static final double ROAD_DETOUR_FACTOR = 1.3;

    /** Average walking speed inside a plant, metres per minute (~4.3 km/h). */
    public static final double WALK_METRES_PER_MINUTE = 72.0;

    private GeoUtils() {
    }

    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    /** Estimated walking minutes between two points (straight line x detour factor). */
    public static int walkMinutes(double lat1, double lng1, double lat2, double lng2) {
        double metres = haversineKm(lat1, lng1, lat2, lng2) * 1000 * ROAD_DETOUR_FACTOR;
        return (int) Math.ceil(metres / WALK_METRES_PER_MINUTE);
    }
}
