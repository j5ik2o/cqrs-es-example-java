package com.github.j5ik2o.cqrs.es.java.rmu;

/** Signals that a shard fell behind the stream retention window, so a full resync is required. */
public final class StreamTrimmedException extends RuntimeException {
  private final String shardId;

  public StreamTrimmedException(String shardId, Throwable cause) {
    super("head stream records for shard " + shardId + " were trimmed", cause);
    this.shardId = shardId;
  }

  public String shardId() {
    return shardId;
  }
}
