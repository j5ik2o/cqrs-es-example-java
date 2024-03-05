package com.github.j5ik2o.cqrs.es.java.command.processor;

public abstract class ProcessException extends RuntimeException {
  protected ProcessException(String message, Throwable cause) {
    super(message, cause);
  }

  protected ProcessException(String message) {
    super(message);
  }
}
