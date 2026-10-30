package dev.pdv.worker.probe.infrastructure;

/** Names are restricted to the dedicated diagnostic namespace. */
public record ProbeTopology(String prefix) {
    public ProbeTopology {
        if (prefix == null || !prefix.matches("pdv\\.(scaffold|test|smoke)\\.[a-zA-Z0-9.-]{1,100}")) {
            throw new IllegalArgumentException("Invalid diagnostic namespace");
        }
    }
    public String exchange() { return prefix + ".exchange"; }
    public String errorsExchange() { return prefix + ".errors"; }
    public String requests() { return prefix + ".requests"; }
    public String responses() { return prefix + ".responses"; }
    public String dead() { return prefix + ".dead"; }
}
