package com.github.j5ik2o.cqrs.es.java.rmu;

import java.time.Instant;

/**
 * The durable record of one event that could not be applied.
 *
 * <p>It stores identifiers only. The event body is always re-read from the journal, so a task
 * cannot go stale when the payload is repaired.
 */
public record RecoveryTask(
    String storeId,
    String projectionName,
    String projectionVersion,
    String aggregateId,
    long seqNr,
    FailureKind failureKind,
    String errorMessage,
    String status,
    Instant createdAt,
    Instant updatedAt) {
  public static final String STATUS_PENDING = "pending";
  public static final String STATUS_COMPLETED = "completed";
}
