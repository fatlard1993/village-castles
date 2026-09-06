package com.villagecastles.generator.ancient;

/**
 * The landscape features an ancient castle can be tailored to. Detection lives in
 * {@link LandformSurvey}; each constant carries the footprint radius its generator designs for.
 */
public enum Landform {
    /** Flat high ground with steep drops around it: a rim-walled citadel. */
    PLATEAU(28),
    /** A rounded summit standing above its surroundings: a terraced ringfort. */
    HILLTOP(20),
    /** A tall face crossing the site: a hold built against and into the wall. */
    CLIFF_FACE(22),
    /** Dry ground ringed by water: a sea fort. */
    ISLAND(16),
    /** Water on three sides, one dry neck: a promontory fort, its whole defense in one wall. */
    HEADLAND(20),
    /** A high spine or the saddle between two highs: an elongated wall-hold, gate through it. */
    RIDGE(22),
    /** A summit too jagged for a ringfort: a lone monumental watchtower with rock-cut steps. */
    EYRIE(14);

    public final int radius;

    Landform(int radius) {
        this.radius = radius;
    }

    public String id() {
        return name().toLowerCase();
    }

    /** Case-insensitive lookup, defaulting to HILLTOP so a corrupt tag still yields a castle. */
    public static Landform byId(String id) {
        for (Landform landform : values()) {
            if (landform.id().equalsIgnoreCase(id)) return landform;
        }
        return HILLTOP;
    }
}
