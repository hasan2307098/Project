package com.netpulse.backend.config;

/**
 * Central configuration resolver.
 *
 * <p>Every setting can be supplied as an environment variable (preferred for
 * deployment) or as a {@code -D} JVM system property (handy during development).
 * Sensible defaults let the service start on a fresh machine with zero config.</p>
 */
public final class AppConfig {

    private AppConfig() {
        // static utility class
    }

    public static String dbUrl() {
        return resolve("NETPULSE_DB_URL", "jdbc:postgresql://localhost:5432/netpulse");
    }

    public static String dbUser() {
        return resolve("NETPULSE_DB_USER", "netpulse");
    }

    public static String dbPassword() {
        return resolve("NETPULSE_DB_PASSWORD", "netpulse");
    }

    public static int dbPoolSize() {
        return resolveInt("NETPULSE_DB_POOL_SIZE", 10);
    }

    public static int httpPort() {
        return resolveInt("NETPULSE_API_PORT", 7070);
    }

    /** Hard ceiling on how many samples a single /api/metrics/batch call may carry. */
    public static int maxBatchSize() {
        return resolveInt("NETPULSE_MAX_BATCH", 500);
    }

    /** Hard ceiling on the number of history rows returned to the chart. */
    public static int maxHistoryLimit() {
        return resolveInt("NETPULSE_MAX_HISTORY", 500);
    }

    // -----------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------

    private static String resolve(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            // system properties use the lower-case dotted convention, e.g. -Dnetpulse.db.url=...
            value = System.getProperty(key.toLowerCase().replace('_', '.'));
        }
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static int resolveInt(String key, int fallback) {
        try {
            return Integer.parseInt(resolve(key, String.valueOf(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
