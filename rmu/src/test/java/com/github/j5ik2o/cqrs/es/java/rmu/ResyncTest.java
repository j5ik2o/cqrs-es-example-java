package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ResyncTest extends AbstractRmuDatabaseTest {

  @Test
  void resyncsEveryHeadAfterAStopLongerThanTheRetentionWindow() {
    var log = RmuTestSupport.createEvents(50);
    var journal = new RmuTestSupport.InMemoryJournalReader();
    journal.putAll(log.aggregateId(), log.events());
    var applier = new Applier(sqlSessionFactory, journal, 1);
    var scanner = new FakeHeadScanner(List.of(new Head(log.aggregateId(), "GroupChat", 50)));
    var resyncer = new Resyncer(applier, scanner, null);

    var result = resyncer.resync();

    assertEquals(1, result.heads());
    assertEquals(1, result.advanced());
    assertEquals(0, result.failed());
    assertEquals(50L, positionOf(log.aggregateId()));
    assertEquals(
        48, count("SELECT COUNT(*) FROM messages WHERE group_chat_id = ?", log.aggregateId()));
  }

  @Test
  void skipsTheConfigurationItemAndOtherAggregateTypes() {
    var scanner =
        new FakeHeadScanner(
            List.of(
                new Head("__config__", "Config", 0),
                new Head("UserAccount-01H42K4ABWQ5V2XQEP3A48VE0Z", "UserAccount", 3)));
    var resyncer = new Resyncer(new Applier(sqlSessionFactory, null, 1), scanner, null);

    var result = resyncer.resync();

    assertEquals(0, result.heads());
    assertEquals(2, result.skipped());
    assertEquals(0, result.advanced());
  }

  @Test
  void recordsAPermanentFailureAndCompletesTheOtherAggregates() {
    var healthy = RmuTestSupport.createFixture();
    var broken = RmuTestSupport.createFixture();
    var journal = new RmuTestSupport.InMemoryJournalReader();
    journal.put(healthy.aid(), RmuTestSupport.headEvent(1, healthy.created()));
    journal.put(healthy.aid(), RmuTestSupport.headEvent(2, healthy.memberAdded()));
    var applier = new Applier(sqlSessionFactory, journal, 1);
    var store = new MyBatisRecoveryTaskStore(sqlSessionFactory);
    var recovery = new RecoveryService(store, () -> RmuTestSupport.STORE_ID, applier, null);
    var scanner =
        new FakeHeadScanner(
            List.of(
                new Head(broken.aid(), "GroupChat", 2), new Head(healthy.aid(), "GroupChat", 2)));
    var resyncer = new Resyncer(applier, scanner, recovery);

    var result = resyncer.resync();

    assertEquals(2, result.heads());
    assertEquals(2, result.advanced());
    assertEquals(1, result.failed());
    assertEquals(2L, positionOf(healthy.aid()));
    assertNull(positionOf(broken.aid()));
    assertEquals(1, store.listPending().size());
    assertEquals(broken.aid(), store.listPending().get(0).aggregateId());

    journal.put(broken.aid(), RmuTestSupport.headEvent(1, broken.created()));
    journal.put(broken.aid(), RmuTestSupport.headEvent(2, broken.memberAdded()));

    assertEquals(1, recovery.recoverPending());

    assertEquals(2L, positionOf(broken.aid()));
    assertEquals(2, count("SELECT COUNT(*) FROM members WHERE group_chat_id = ?", broken.aid()));
    assertEquals(0, store.listPending().size());
  }

  private static final class FakeHeadScanner implements HeadScanner {
    private final List<Head> heads;

    private FakeHeadScanner(List<Head> heads) {
      this.heads = heads;
    }

    @Override
    public void scanHeads(Consumer<Head> visitor) {
      heads.forEach(visitor);
    }
  }
}
