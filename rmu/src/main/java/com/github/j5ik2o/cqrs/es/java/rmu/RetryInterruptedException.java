package com.github.j5ik2o.cqrs.es.java.rmu;

/** Signals that a retry wait was interrupted, so the caller must stop instead of retrying. */
public final class RetryInterruptedException extends RuntimeException {
  public RetryInterruptedException(InterruptedException cause) {
    super("retry wait was interrupted", cause);
  }
}
