package com.github.j5ik2o.cqrs.es.java.processor;

public final class NotFoundProcessException extends ProcessException {
  public NotFoundProcessException(String message) {
    super(message);
  }
}
