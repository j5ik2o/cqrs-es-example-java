package com.github.j5ik2o.cqrs.es.java.rmu;

/** One entry of the head table after the configuration item is removed. */
public record Head(String aggregateId, String typeName, long seqNr) {}
