package com.github.j5ik2o.cqrs.es.java.rmu;

/**
 * Applies events to the read model under the single position rule: only the event at {@code
 * position + 1} is applied, and the read model change and the position advance commit together.
 *
 * <p>The stream path, the resync path, and the DLQ replay path all use this interface.
 */
public interface EventApplier {
  /** Returns the last applied seq_nr for the aggregate. */
  long position(String aggregateId);

  /** Applies one head record event. The journal fills a gap below the record's seq_nr. */
  void applyHeadEvent(String aggregateId, HeadEvent event);

  /** Replays the journal from the current position up to {@code targetSeqNr}. */
  void applyThrough(String aggregateId, long targetSeqNr);
}
