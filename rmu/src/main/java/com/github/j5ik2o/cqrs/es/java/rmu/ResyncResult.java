package com.github.j5ik2o.cqrs.es.java.rmu;

/** Counts what one full reconciliation saw and did. */
public record ResyncResult(int heads, int skipped, int advanced, int failed) {}
