package com.github.j5ik2o.cqrs.es.java.rmu;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Records failed events and replays them from the journal.
 *
 * <p>This is the DLQ half of the RMU contract. The task is durable, the replay is idempotent, and
 * completion is claimed only after the position reaches the task's seq_nr.
 */
public final class RecoveryService {
  private static final Logger LOGGER = LoggerFactory.getLogger(RecoveryService.class);

  private final RecoveryTaskStore taskStore;
  private final StoreIdProvider storeIdProvider;
  private final EventApplier applier;
  private final RecoveryNotifier notifier;

  public RecoveryService(
      RecoveryTaskStore taskStore,
      StoreIdProvider storeIdProvider,
      EventApplier applier,
      RecoveryNotifier notifier) {
    this.taskStore = taskStore;
    this.storeIdProvider = storeIdProvider;
    this.applier = applier;
    this.notifier = notifier;
  }

  /**
   * Persists a recovery task. A failure here is returned to the caller on purpose: losing the task
   * would hide a permanently failing event.
   */
  public void record(String aggregateId, long seqNr, Throwable failure) {
    var kind = FailureClassifier.classify(failure).orElse(FailureKind.UNKNOWN);
    var storeId = storeIdProvider.storeId();
    var now = Instant.now();
    var task =
        new RecoveryTask(
            storeId,
            Projection.NAME,
            Projection.VERSION,
            aggregateId,
            seqNr,
            kind,
            failure == null ? "" : failure.getMessage(),
            RecoveryTask.STATUS_PENDING,
            now,
            now);
    taskStore.record(task);
    if (notifier != null) {
      try {
        notifier.notifyTask(task);
      } catch (RuntimeException e) {
        // The durable task already exists. A notification failure only means the worker is not
        // woken immediately; the next recovery run still sees the task.
        LOGGER.warn("Failed to notify a recovery task: aid={}, seqNr={}", aggregateId, seqNr, e);
      }
    }
  }

  /**
   * Replays one task. It re-reads the position first, so calling it twice is safe: the second call
   * finds the position at or beyond the task and only marks the task complete.
   */
  public void recover(RecoveryTask task) {
    var position = applier.position(task.aggregateId());
    if (position < task.seqNr()) {
      applier.applyThrough(task.aggregateId(), task.seqNr());
    }
    var confirmed = applier.position(task.aggregateId());
    if (confirmed < task.seqNr()) {
      throw new RecoveryException(
          "aggregate %s stayed at position %d, expected %d"
              .formatted(task.aggregateId(), confirmed, task.seqNr()));
    }
    taskStore.markCompleted(
        task.storeId(), task.projectionName(), task.aggregateId(), task.seqNr());
  }

  /** Replays every pending task. One failing aggregate does not stop the others. */
  public int recoverPending() {
    var tasks = taskStore.listPending();
    var completed = 0;
    RuntimeException failure = null;
    for (var task : new ArrayList<>(tasks)) {
      try {
        recover(task);
        completed++;
      } catch (RuntimeException e) {
        if (failure == null) {
          failure = new RecoveryException("some recovery tasks remain pending", e);
        } else {
          failure.addSuppressed(e);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
    return completed;
  }

  public List<RecoveryTask> pendingTasks() {
    return taskStore.listPending();
  }
}
