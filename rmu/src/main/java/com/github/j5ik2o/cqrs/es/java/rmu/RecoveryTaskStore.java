package com.github.j5ik2o.cqrs.es.java.rmu;

import java.util.List;

/**
 * Persists recovery tasks. Recording is idempotent on the (store, projection, aggregate, seq_nr)
 * key so a redelivered record does not create duplicates.
 */
public interface RecoveryTaskStore {
  void record(RecoveryTask task);

  List<RecoveryTask> listPending();

  void markCompleted(String storeId, String projectionName, String aggregateId, long seqNr);

  /** Records a failed recovery attempt and moves the task behind tasks not yet attempted. */
  void markFailed(RecoveryTask task, Throwable failure);
}
