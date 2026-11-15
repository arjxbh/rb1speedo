package club.midlife.rb1speed;

/** Ambient-light curve and approximate solar altitude; no Android dependencies. */
public final class DisplayBrightness {
    private DisplayBrightness() {}

    public static float level(float lux, boolean night) {
        if (!Float.isFinite(lux) || lux < 0) return night ? .08f : -1f;
        // Logarithmic response keeps dark cabins dim, with full output in sunlight.
        double fraction = Math.log1p(lux) / Math.log1p(10000);
        return (float)Math.max(.06, Math.min(1, .06 + .94 * fraction));
    }

    public static boolean isNight(long utcMillis, double latitude, double longitude) {
        // NOAA fractional-year approximation, evaluated in UTC. Handles polar day/night.
        java.util.Calendar utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        utc.setTimeInMillis(utcMillis);
        double hour = utc.get(java.util.Calendar.HOUR_OF_DAY) + utc.get(java.util.Calendar.MINUTE) / 60.0;
        double year = 2 * Math.PI / 365 * (utc.get(java.util.Calendar.DAY_OF_YEAR) - 1 + (hour - 12) / 24);
        double equation = 229.18 * (.000075 + .001868 * Math.cos(year) - .032077 * Math.sin(year)
            - .014615 * Math.cos(2 * year) - .040849 * Math.sin(2 * year));
        double declination = .006918 - .399912 * Math.cos(year) + .070257 * Math.sin(year)
            - .006758 * Math.cos(2 * year) + .000907 * Math.sin(2 * year)
            - .002697 * Math.cos(3 * year) + .00148 * Math.sin(3 * year);
        double angle = Math.toRadians((hour * 60 + equation + 4 * longitude) / 4 - 180);
        double lat = Math.toRadians(latitude);
        double altitude = Math.asin(Math.sin(lat) * Math.sin(declination)
            + Math.cos(lat) * Math.cos(declination) * Math.cos(angle));
        return altitude < Math.toRadians(-.833);
    }
}
