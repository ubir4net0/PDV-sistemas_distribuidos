package dev.pdv.api.support;

/** Explicit production baseline, independent of Flyway's detected current version. */
public final class MigrationBaseline {
    public static final int COUNT = 15;
    public static final String VERSION = "15";
    private MigrationBaseline() { }
}
