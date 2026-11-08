package com.github.j5ik2o.cqrs.es.java.rmu;

import java.time.Instant;

/**
 * One event envelope carried by a head item's {@code events} list.
 *
 * <p>The head stream only carries the aggregate's latest transition. Events that were skipped
 * because the stream paused past their retention are read from the journal and converted to the
 * same shape, so both paths apply the read model through one code path.
 */
public record HeadEvent(long seqNr, Instant occurredAt, String manifest, byte[] payload) {}
