package dev.pdv.worker.business;

public record BusinessTopology(String prefix) {
    public BusinessTopology {
        if (prefix==null || !prefix.matches("pdv\\.(business|test|smoke)\\.[a-zA-Z0-9.-]{1,100}")) { throw new IllegalArgumentException("Invalid business namespace"); }
    }
    public String exchange() { return prefix+".exchange"; }
    public String errors() { return prefix+".errors"; }
    public String requests() { return prefix+".requests"; }
    public String results() { return prefix+".results"; }
    public String dead() { return prefix+".dead"; }
}
