package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.graphql;

/** Raised when a single-value read model query finds no row for the requested account. */
public class ReadModelNotFoundException extends RuntimeException {

  public ReadModelNotFoundException(String resource) {
    super(resource + " not found.");
  }
}
