package com.github.j5ik2o.cqrs.es.java.rmu;

/**
 * Marks a failure that retrying the same record cannot fix. Only payload, gap, and constraint
 * problems are permanent; storage outages stay unclassified so the caller retries them.
 */
public final class PermanentFailure extends ReadModelUpdateException {
  private final FailureKind kind;

  public PermanentFailure(FailureKind kind, String message) {
    super(message);
    this.kind = kind;
  }

  public PermanentFailure(FailureKind kind, String message, Throwable cause) {
    super(message, cause);
    this.kind = kind;
  }

  public FailureKind kind() {
    return kind;
  }

  static RuntimeException permanent(FailureKind kind, String message) {
    return new PermanentFailure(kind, message);
  }

  static RuntimeException permanent(FailureKind kind, String message, Throwable cause) {
    return new PermanentFailure(kind, message, cause);
  }
}
