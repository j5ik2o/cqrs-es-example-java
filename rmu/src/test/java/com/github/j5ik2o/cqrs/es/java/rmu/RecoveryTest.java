package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RecoveryTest extends AbstractRmuDatabaseTest {

  @Test
  void retriesATransientFailureUntilSuccess() {
    var applier = new FlakyApplier(2);
    var updater = new ReadModelUpdater(applier, null, testRetryPolicy(5));
    var record =
        RmuTestSupport.headRecord(
            "GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z",
            "MODIFY",
            List.of(RmuTestSupport.headEvent(1, RmuTestSupport.createFixture().created())));

    updater.update(record);

    assertEquals(3, applier.calls);
    assertEquals(1L, applier.position);
  }

  @Test
  void recordsAnExhaustedRetryAndRecoversFromTheJournal() {
    var fixture = RmuTestSupport.createFixture();
    var journal = new RmuTestSupport.InMemoryJournalReader();
    journal.put(fixture.aid(), RmuTestSupport.headEvent(1, fixture.created()));
    journal.put(fixture.aid(), RmuTestSupport.headEvent(2, fixture.memberAdded()));
    var applier = new Applier(sqlSessionFactory, new FlakyJournalReader(journal, 3), 1);
    var store = new MyBatisRecoveryTaskStore(sqlSessionFactory);
    var recovery = new RecoveryService(store, () -> RmuTestSupport.STORE_ID, applier, null);
    var updater = new ReadModelUpdater(applier, recovery, testRetryPolicy(3));

    updater.update(
        RmuTestSupport.headRecord(
            fixture.aid(), "MODIFY", List.of(RmuTestSupport.headEvent(2, fixture.memberAdded()))));

    assertNull(positionOf(fixture.aid()));
    var pending = store.listPending();
    assertEquals(1, pending.size());
    assertEquals(fixture.aid(), pending.get(0).aggregateId());
    assertEquals(2L, pending.get(0).seqNr());
    assertEquals(FailureKind.UNKNOWN, pending.get(0).failureKind());

    assertEquals(1, recovery.recoverPending());

    assertEquals(2L, positionOf(fixture.aid()));
    assertEquals(2, count("SELECT COUNT(*) FROM members WHERE group_chat_id = ?", fixture.aid()));
    assertTrue(store.listPending().isEmpty());
  }

  @Test
  void recordsAPermanentFailureAndRecoversFromTheJournal() {
    var fixture = RmuTestSupport.createFixture();
    var journal = new RmuTestSupport.InMemoryJournalReader();
    journal.put(fixture.aid(), RmuTestSupport.headEvent(1, fixture.created()));
    var applier = new Applier(sqlSessionFactory, journal, 1);
    var store = new MyBatisRecoveryTaskStore(sqlSessionFactory);
    var recovery = new RecoveryService(store, () -> RmuTestSupport.STORE_ID, applier, null);
    var updater = new ReadModelUpdater(applier, recovery, testRetryPolicy(3));

    updater.update(
        RmuTestSupport.headRecord(
            fixture.aid(), "INSERT", List.of(RmuTestSupport.invalidHeadEvent(1))));

    assertNull(positionOf(fixture.aid()));
    var pending = store.listPending();
    assertEquals(1, pending.size());
    assertEquals(FailureKind.EVENT_PAYLOAD, pending.get(0).failureKind());

    assertEquals(1, recovery.recoverPending());

    assertEquals(1L, positionOf(fixture.aid()));
    assertEquals(1, count("SELECT COUNT(*) FROM group_chats WHERE id = ?", fixture.aid()));
    assertTrue(store.listPending().isEmpty());
  }

  @Test
  void recoveryIsIdempotent() {
    var fixture = RmuTestSupport.createFixture();
    var journal = new RmuTestSupport.InMemoryJournalReader();
    journal.put(fixture.aid(), RmuTestSupport.headEvent(1, fixture.created()));
    var applier = new Applier(sqlSessionFactory, journal, 1);
    var store = new MyBatisRecoveryTaskStore(sqlSessionFactory);
    var recovery = new RecoveryService(store, () -> RmuTestSupport.STORE_ID, applier, null);
    var task =
        new RecoveryTask(
            RmuTestSupport.STORE_ID,
            Projection.NAME,
            Projection.VERSION,
            fixture.aid(),
            1,
            FailureKind.EVENT_PAYLOAD,
            "test",
            RecoveryTask.STATUS_PENDING,
            null,
            null);
    store.record(task);

    recovery.recover(task);
    recovery.recover(task);

    assertEquals(1L, positionOf(fixture.aid()));
    assertEquals(1, count("SELECT COUNT(*) FROM group_chats WHERE id = ?", fixture.aid()));
    assertTrue(store.listPending().isEmpty());
  }

  @Test
  void recoveryFailsWhenThePositionDoesNotReachTheTarget() {
    var task =
        new RecoveryTask(
            RmuTestSupport.STORE_ID,
            Projection.NAME,
            Projection.VERSION,
            "GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z",
            5,
            FailureKind.EVENT_PAYLOAD,
            "test",
            RecoveryTask.STATUS_PENDING,
            null,
            null);
    var store = new InMemoryRecoveryTaskStore();
    store.record(task);
    var applier = new NonAdvancingApplier();
    var recovery = new RecoveryService(store, () -> RmuTestSupport.STORE_ID, applier, null);

    assertThrows(RecoveryException.class, recovery::recoverPending);
    assertTrue(store.pending.contains(task));
  }

  @Test
  void returnsARecoveryStoreWriteFailureToTheCaller() {
    var fixture = RmuTestSupport.createFixture();
    var applier = new Applier(sqlSessionFactory, new RmuTestSupport.InMemoryJournalReader(), 1);
    var failingStore =
        new RecoveryTaskStore() {
          @Override
          public void record(RecoveryTask task) {
            throw new IllegalStateException("recovery store is down");
          }

          @Override
          public List<RecoveryTask> listPending() {
            return List.of();
          }

          @Override
          public void markCompleted(
              String storeId, String projectionName, String aggregateId, long seqNr) {}

          @Override
          public void markFailed(RecoveryTask task, Throwable failure) {}
        };
    var recovery = new RecoveryService(failingStore, () -> RmuTestSupport.STORE_ID, applier, null);
    var updater = new ReadModelUpdater(applier, recovery, testRetryPolicy(3));

    assertThrows(
        IllegalStateException.class,
        () ->
            updater.update(
                RmuTestSupport.headRecord(
                    fixture.aid(), "INSERT", List.of(RmuTestSupport.invalidHeadEvent(1)))));
  }

  @Test
  void appliesTheSameRecordConcurrentlyExactlyOnce() throws Exception {
    var fixture = RmuTestSupport.createFixture();
    var journal = new RmuTestSupport.InMemoryJournalReader();
    var applier = new Applier(sqlSessionFactory, journal, 1);
    var events =
        List.of(
            RmuTestSupport.headEvent(1, fixture.created()),
            RmuTestSupport.headEvent(2, fixture.memberAdded()),
            RmuTestSupport.headEvent(3, fixture.messagePosted()));

    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var futures =
          List.of(
              executor.submit(() -> applyAll(applier, fixture.aid(), events, start)),
              executor.submit(() -> applyAll(applier, fixture.aid(), events, start)));
      start.countDown();
      for (var future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertEquals(3L, positionOf(fixture.aid()));
    assertEquals(1, count("SELECT COUNT(*) FROM group_chats WHERE id = ?", fixture.aid()));
    assertEquals(2, count("SELECT COUNT(*) FROM members WHERE group_chat_id = ?", fixture.aid()));
    assertEquals(1, count("SELECT COUNT(*) FROM messages WHERE group_chat_id = ?", fixture.aid()));
  }

  @Test
  void createsPositionRowsForDifferentAggregatesConcurrently() throws Exception {
    var first = RmuTestSupport.createFixture();
    var second = RmuTestSupport.createFixture();
    var applier = new Applier(sqlSessionFactory, new RmuTestSupport.InMemoryJournalReader(), 1);

    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var futures =
          List.of(
              executor.submit(
                  () ->
                      applyAll(
                          applier,
                          first.aid(),
                          List.of(RmuTestSupport.headEvent(1, first.created())),
                          start)),
              executor.submit(
                  () ->
                      applyAll(
                          applier,
                          second.aid(),
                          List.of(RmuTestSupport.headEvent(1, second.created())),
                          start)));
      start.countDown();
      for (var future : futures) {
        future.get(30, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertEquals(1L, positionOf(first.aid()));
    assertEquals(1L, positionOf(second.aid()));
    assertEquals(2, count("SELECT COUNT(*) FROM group_chats"));
  }

  @Test
  void dropsAnOutOfOrderStaleEvent() {
    var fixture = RmuTestSupport.createFixture();
    var journal = new RmuTestSupport.InMemoryJournalReader();
    var applier = new Applier(sqlSessionFactory, journal, 1);

    applyAll(
        applier,
        fixture.aid(),
        List.of(
            RmuTestSupport.headEvent(1, fixture.created()),
            RmuTestSupport.headEvent(2, fixture.memberAdded()),
            RmuTestSupport.headEvent(3, fixture.messagePosted())),
        new CountDownLatch(0));

    applyAll(
        applier,
        fixture.aid(),
        List.of(RmuTestSupport.headEvent(2, fixture.memberAdded())),
        new CountDownLatch(0));

    assertEquals(3L, positionOf(fixture.aid()));
    assertEquals(1, count("SELECT COUNT(*) FROM messages WHERE group_chat_id = ?", fixture.aid()));
  }

  @Test
  void recordsAHeadParseFailureAsARecoveryTask() {
    var fixture = RmuTestSupport.createFixture();
    var journal = new RmuTestSupport.InMemoryJournalReader();
    journal.put(fixture.aid(), RmuTestSupport.headEvent(1, fixture.created()));
    journal.put(fixture.aid(), RmuTestSupport.headEvent(2, fixture.memberAdded()));
    var applier = new Applier(sqlSessionFactory, journal, 1);
    var store = new MyBatisRecoveryTaskStore(sqlSessionFactory);
    var recovery = new RecoveryService(store, () -> RmuTestSupport.STORE_ID, applier, null);
    var updater = new ReadModelUpdater(applier, recovery, testRetryPolicy(3));

    updater.update(RmuTestSupport.headRecordWithoutEvents(fixture.aid(), 2));

    assertNull(positionOf(fixture.aid()));
    var pending = store.listPending();
    assertEquals(1, pending.size());
    assertEquals(2L, pending.get(0).seqNr());
    assertEquals(FailureKind.EVENT_PAYLOAD, pending.get(0).failureKind());

    assertEquals(1, recovery.recoverPending());
    assertEquals(2L, positionOf(fixture.aid()));
  }

  @Test
  void recoveryTargetCoversTheRestOfAMultiEventRecord() {
    var fixture = RmuTestSupport.createFixture();
    var journal = new RmuTestSupport.InMemoryJournalReader();
    journal.put(fixture.aid(), RmuTestSupport.headEvent(1, fixture.created()));
    journal.put(fixture.aid(), RmuTestSupport.headEvent(2, fixture.memberAdded()));
    journal.put(fixture.aid(), RmuTestSupport.headEvent(3, fixture.messagePosted()));
    var applier = new Applier(sqlSessionFactory, journal, 1);
    var store = new MyBatisRecoveryTaskStore(sqlSessionFactory);
    var recovery = new RecoveryService(store, () -> RmuTestSupport.STORE_ID, applier, null);
    var updater = new ReadModelUpdater(applier, recovery, testRetryPolicy(1));

    updater.update(
        RmuTestSupport.headRecord(
            fixture.aid(),
            "MODIFY",
            List.of(
                RmuTestSupport.headEvent(1, fixture.created()),
                RmuTestSupport.headEvent(2, fixture.memberAdded()),
                RmuTestSupport.invalidHeadEvent(3))));

    assertEquals(2L, positionOf(fixture.aid()));
    var pending = store.listPending();
    assertEquals(1, pending.size());
    assertEquals(3L, pending.get(0).seqNr());

    assertEquals(1, recovery.recoverPending());
    assertEquals(3L, positionOf(fixture.aid()));
    assertEquals(1, count("SELECT COUNT(*) FROM messages WHERE group_chat_id = ?", fixture.aid()));
  }

  @Test
  void skipsAndRejectsARecoveryTaskFromAnotherProjection() {
    var task =
        new RecoveryTask(
            "another-store",
            Projection.NAME,
            Projection.VERSION,
            "GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z",
            1,
            FailureKind.EVENT_PAYLOAD,
            "test",
            RecoveryTask.STATUS_PENDING,
            null,
            null);
    var store = new InMemoryRecoveryTaskStore();
    store.record(task);
    var recovery =
        new RecoveryService(store, () -> RmuTestSupport.STORE_ID, new NonAdvancingApplier(), null);

    assertEquals(0, recovery.recoverPending());
    assertTrue(store.pending.contains(task));
    assertThrows(RecoveryException.class, () -> recovery.recover(task));
  }

  @Test
  void usesTheHeadAggregateIdForThePositionKey() {
    var fixture = RmuTestSupport.createFixture();
    var rawAggregateId =
        "GroupChat-" + fixture.aid().substring("GroupChat-".length()).toLowerCase(Locale.ROOT);
    var journal = new RmuTestSupport.InMemoryJournalReader();
    journal.put(rawAggregateId, RmuTestSupport.headEvent(1, fixture.created()));
    var applier = new Applier(sqlSessionFactory, journal, 1);

    applier.applyHeadEvent(rawAggregateId, RmuTestSupport.headEvent(1, fixture.created()));

    assertEquals(1L, positionOf(rawAggregateId));
    assertEquals(1, count("SELECT COUNT(*) FROM group_chats WHERE id = ?", fixture.aid()));
  }

  @Test
  void propagatesAnInterruptDuringRecovery() {
    var task =
        new RecoveryTask(
            RmuTestSupport.STORE_ID,
            Projection.NAME,
            Projection.VERSION,
            "GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z",
            1,
            FailureKind.EVENT_PAYLOAD,
            "test",
            RecoveryTask.STATUS_PENDING,
            null,
            null);
    var store = new InMemoryRecoveryTaskStore();
    store.record(task);
    var interruptingApplier =
        new EventApplier() {
          @Override
          public long position(String aggregateId) {
            return 0L;
          }

          @Override
          public void applyHeadEvent(String aggregateId, HeadEvent event) {}

          @Override
          public void applyThrough(String aggregateId, long targetSeqNr) {
            throw new RetryInterruptedException(new InterruptedException("stopped"));
          }
        };
    var recovery =
        new RecoveryService(store, () -> RmuTestSupport.STORE_ID, interruptingApplier, null);

    assertThrows(RetryInterruptedException.class, recovery::recoverPending);
  }

  private static void applyAll(
      EventApplier applier, String aggregateId, List<HeadEvent> events, CountDownLatch start) {
    try {
      start.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
    for (var event : events) {
      applier.applyHeadEvent(aggregateId, event);
    }
  }

  private static final class FlakyApplier implements EventApplier {
    private final int failuresBeforeSuccess;
    private int calls;
    private long position;

    private FlakyApplier(int failuresBeforeSuccess) {
      this.failuresBeforeSuccess = failuresBeforeSuccess;
    }

    @Override
    public long position(String aggregateId) {
      return position;
    }

    @Override
    public void applyHeadEvent(String aggregateId, HeadEvent event) {
      calls++;
      if (calls <= failuresBeforeSuccess) {
        throw new RuntimeException("transient storage failure");
      }
      position = event.seqNr();
    }

    @Override
    public void applyThrough(String aggregateId, long targetSeqNr) {
      position = targetSeqNr;
    }
  }

  private static final class NonAdvancingApplier implements EventApplier {
    @Override
    public long position(String aggregateId) {
      return 0L;
    }

    @Override
    public void applyHeadEvent(String aggregateId, HeadEvent event) {}

    @Override
    public void applyThrough(String aggregateId, long targetSeqNr) {}
  }

  private static final class FlakyJournalReader implements JournalReader {
    private final JournalReader delegate;
    private int failuresRemaining;

    private FlakyJournalReader(JournalReader delegate, int failuresRemaining) {
      this.delegate = delegate;
      this.failuresRemaining = failuresRemaining;
    }

    @Override
    public List<HeadEvent> readEvents(String aggregateId, long fromSeqNr, long toSeqNr) {
      if (failuresRemaining > 0) {
        failuresRemaining--;
        throw new ReadModelUpdateException("transient storage failure");
      }
      return delegate.readEvents(aggregateId, fromSeqNr, toSeqNr);
    }
  }

  private static final class InMemoryRecoveryTaskStore implements RecoveryTaskStore {
    private final List<RecoveryTask> pending = new ArrayList<>();

    @Override
    public void record(RecoveryTask task) {
      pending.removeIf(
          current ->
              current.storeId().equals(task.storeId())
                  && current.projectionName().equals(task.projectionName())
                  && current.aggregateId().equals(task.aggregateId())
                  && current.seqNr() == task.seqNr());
      pending.add(task);
    }

    @Override
    public List<RecoveryTask> listPending() {
      return List.copyOf(pending);
    }

    @Override
    public void markCompleted(
        String storeId, String projectionName, String aggregateId, long seqNr) {
      pending.removeIf(
          task ->
              task.storeId().equals(storeId)
                  && task.projectionName().equals(projectionName)
                  && task.aggregateId().equals(aggregateId)
                  && task.seqNr() == seqNr);
    }

    @Override
    public void markFailed(RecoveryTask task, Throwable failure) {
      // Keep the task pending; the next run retries it.
    }
  }
}
