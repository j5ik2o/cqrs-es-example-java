package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecoveryNotifierTest {

  @Test
  void notifiesAfterTheTaskIsDurablyRecorded() {
    var store = new CapturingRecoveryTaskStore();
    var notified = new ArrayList<RecoveryTask>();
    var recovery =
        new RecoveryService(store, () -> RmuTestSupport.STORE_ID, new NoopApplier(), notified::add);

    recovery.record(
        "GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z",
        3,
        new PermanentFailure(FailureKind.EVENT_PAYLOAD, "bad payload"));

    assertEquals(1, store.recorded.size());
    assertEquals(1, notified.size());
    assertEquals(3L, notified.get(0).seqNr());
    assertEquals(3L, store.recorded.get(0).seqNr());
  }

  @Test
  void keepsTheDurableTaskWhenTheNotificationFails() {
    var store = new CapturingRecoveryTaskStore();
    var recovery =
        new RecoveryService(
            store,
            () -> RmuTestSupport.STORE_ID,
            new NoopApplier(),
            task -> {
              throw new IllegalStateException("sqs is unavailable");
            });

    recovery.record(
        "GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z",
        3,
        new PermanentFailure(FailureKind.EVENT_PAYLOAD, "bad payload"));

    assertEquals(1, store.recorded.size());
  }

  private static final class CapturingRecoveryTaskStore implements RecoveryTaskStore {
    private final List<RecoveryTask> recorded = new ArrayList<>();

    @Override
    public void record(RecoveryTask task) {
      recorded.add(task);
    }

    @Override
    public List<RecoveryTask> listPending() {
      return List.copyOf(recorded);
    }

    @Override
    public void markCompleted(
        String storeId, String projectionName, String aggregateId, long seqNr) {}

    @Override
    public void markFailed(RecoveryTask task, Throwable failure) {}
  }

  private static final class NoopApplier implements EventApplier {
    @Override
    public long position(String aggregateId) {
      return 0L;
    }

    @Override
    public void applyHeadEvent(String aggregateId, HeadEvent event) {}

    @Override
    public void applyThrough(String aggregateId, long targetSeqNr) {}
  }
}
