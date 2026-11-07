package com.github.j5ik2o.cqrs.es.java.rmu;

/** Signals that a head stream record could not be applied to the read model. */
public class ReadModelUpdateException extends RuntimeException {
  public ReadModelUpdateException(String message) {
    super(message);
  }

  public ReadModelUpdateException(String message, Throwable cause) {
    super(message, cause);
  }
}
