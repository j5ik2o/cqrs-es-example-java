package com.github.j5ik2o.cqrs.es.java.rmu;

import java.util.List;

/**
 * Reads events that a head stream record skipped. The head stream only carries the latest
 * transition, so a gap in {@code seq_nr} is filled from the journal before the head event is
 * applied.
 */
public interface JournalReader {

  /**
   * Returns the events with {@code fromSeqNr <= seq_nr <= toSeqNr} in ascending order, using a
   * strongly consistent read.
   */
  List<HeadEvent> readEvents(String aggregateId, long fromSeqNr, long toSeqNr);
}
