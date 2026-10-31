package dev.pdv.api.support;

/** Explicit production baseline, independent of Flyway's detected current version. */
public final class MigrationBaseline {
    public static final int COUNT = 5;
    public static final String VERSION = "5";
    private MigrationBaseline() { }
}
