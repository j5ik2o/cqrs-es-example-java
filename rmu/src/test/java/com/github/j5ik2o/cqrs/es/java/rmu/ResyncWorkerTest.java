package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ResyncWorkerTest {

  @Test
  void reportsWhatTheResyncDid() {
    var store = new RmuTestSupport.InMemoryRecoveryTaskStore();
    var applier = new AdvancingApplier();
    var recovery = new RecoveryService(store, () -> RmuTestSupport.STORE_ID, applier, null);
    var scanner =
        (HeadScanner)
            visitor ->
                visitor.accept(new Head("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z", "GroupChat", 2));
    var worker = new ResyncWorker(new Resyncer(applier, scanner, recovery));

    var response = worker.handleRequest(null, null);

    assertEquals(1, response.get("heads"));
    assertEquals(0, response.get("skipped"));
    assertEquals(1, response.get("advanced"));
    assertEquals(0, response.get("failed"));
  }

  private static final class AdvancingApplier implements EventApplier {
    private long position;

    @Override
    public long position(String aggregateId) {
      return position;
    }

    @Override
    public void applyHeadEvent(String aggregateId, HeadEvent event) {
      position = event.seqNr();
    }

    @Override
    public void applyThrough(String aggregateId, long targetSeqNr) {
      position = targetSeqNr;
    }
  }
}
