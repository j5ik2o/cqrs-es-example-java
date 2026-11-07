package com.github.j5ik2o.cqrs.es.java.rmu;

import java.time.Instant;
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
  private static final int DEFAULT_MAX_TASKS_PER_RUN = 50;

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

  public void record(String aggregateId, long targetSeqNr, Throwable failure) {
    record(aggregateId, targetSeqNr, targetSeqNr, failure);
  }

  /**
   * Persists a recovery task. {@code targetSeqNr} is the position recovery must reach; {@code
   * failedSeqNr} is the event that failed and is kept in the message for diagnosis. A failure here
   * is returned to the caller on purpose: losing the task would hide a permanently failing event.
   */
  public void record(String aggregateId, long targetSeqNr, long failedSeqNr, Throwable failure) {
    var kind = FailureClassifier.classify(failure).orElse(FailureKind.UNKNOWN);
    var storeId = storeIdProvider.storeId();
    var now = Instant.now();
    var message =
        "failed at seq_nr %d: %s"
            .formatted(failedSeqNr, failure == null ? "" : failure.getMessage());
    var task =
        new RecoveryTask(
            storeId,
            Projection.NAME,
            Projection.VERSION,
            aggregateId,
            targetSeqNr,
            kind,
            message,
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
        LOGGER.warn(
            "Failed to notify a recovery task: aid={}, seqNr={}", aggregateId, targetSeqNr, e);
      }
    }
  }

  /**
   * Replays one task. It re-reads the position first, so calling it twice is safe: the second call
   * finds the position at or beyond the task and only marks the task complete.
   */
  public void recover(RecoveryTask task) {
    var storeId = storeIdProvider.storeId();
    if (!belongsToCurrentProjection(task, storeId)) {
      throw new RecoveryException(
          "recovery task belongs to another store or projection: storeId=%s, projection=%s@%s"
              .formatted(task.storeId(), task.projectionName(), task.projectionVersion()));
    }
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

  /** Replays at most one bounded batch of pending tasks. */
  public int recoverPending() {
    return recoverPending(DEFAULT_MAX_TASKS_PER_RUN);
  }

  /**
   * Replays a bounded number of pending tasks so a large backlog does not stop stream polling. A
   * task that fails is marked as retried, which moves it behind tasks that have not been attempted
   * yet. The task stays pending for the next run.
   */
  public int recoverPending(int maxTasks) {
    var storeId = storeIdProvider.storeId();
    var tasks = taskStore.listPending();
    var completed = 0;
    var processed = 0;
    RuntimeException failure = null;
    for (var task : tasks) {
      if (processed >= maxTasks) {
        break;
      }
      if (!belongsToCurrentProjection(task, storeId)) {
        LOGGER.warn(
            "Skipping a recovery task from another store or projection: storeId={}, projection={}@{}",
            task.storeId(),
            task.projectionName(),
            task.projectionVersion());
        continue;
      }
      processed++;
      try {
        recover(task);
        completed++;
      } catch (RetryInterruptedException e) {
        throw e;
      } catch (RuntimeException e) {
        markFailed(task, e);
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

  private static boolean belongsToCurrentProjection(RecoveryTask task, String storeId) {
    return storeId.equals(task.storeId())
        && Projection.NAME.equals(task.projectionName())
        && Projection.VERSION.equals(task.projectionVersion());
  }

  private void markFailed(RecoveryTask task, RuntimeException failure) {
    try {
      taskStore.markFailed(task, failure);
    } catch (RuntimeException markError) {
      failure.addSuppressed(markError);
    }
  }
}
