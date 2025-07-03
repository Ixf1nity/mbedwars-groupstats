package me.infinity.groupstats.models;

/**
 * Utility class defining canonical short keys for player statistics.
 * These short keys are used consistently across Redis, MongoDB,
 * internal application logic, and PlaceholderAPI placeholders
 * for optimization and standardization.
 */
public final class StatKeys {

    // Private constructor to prevent instantiation
    private StatKeys() {}

    public static final String GAMES_PLAYED = "gp";
    public static final String BEDS_BROKEN = "bb";
    public static final String BEDS_LOST = "bl";
    public static final String KILLS = "k";
    public static final String DEATHS = "d";
    public static final String FINAL_KILLS = "fk";
    public static final String FINAL_DEATHS = "fd";
    public static final String WINS = "w";
    public static final String LOSSES = "l";
    public static final String WINSTREAK = "ws";
    public static final String HIGHEST_WINSTREAK = "hws";

    // Add any other stats that might be tracked if they come from GroupNode or elsewhere
    // For example, if GroupNode had more fields, they would be mapped here too.
    // Example: public static final String PROJECTILES_FIRED = "pf";
}
