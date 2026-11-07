package com.github.j5ik2o.cqrs.es.java.rmu;

/** Returns the store identifier shared by the three DynamoDB tables. */
@FunctionalInterface
public interface StoreIdProvider {
  String storeId();
}
