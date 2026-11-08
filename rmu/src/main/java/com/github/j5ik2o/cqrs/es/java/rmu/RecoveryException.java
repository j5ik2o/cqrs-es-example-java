package com.github.j5ik2o.cqrs.es.java.rmu;

/** Signals that a recovery pass did not complete every pending task. */
public final class RecoveryException extends RuntimeException {
  public RecoveryException(String message) {
    super(message);
  }

  public RecoveryException(String message, Throwable cause) {
    super(message, cause);
  }
}
