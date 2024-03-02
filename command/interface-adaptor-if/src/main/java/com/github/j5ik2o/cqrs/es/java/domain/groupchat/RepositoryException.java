package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

public final class RepositoryException extends RuntimeException {
  public RepositoryException(String message, Throwable cause) {
    super(message, cause);
  }

  public RepositoryException(String message) {
    super(message);
  }
}
