package com.github.j5ik2o.cqrs.es.java.rmu;

/**
 * Delivers a recovery notification. SQS is used only to wake a worker; the message carries
 * identifiers, never the event body. The durable task in the recovery store is the source of truth.
 */
@FunctionalInterface
public interface RecoveryNotifier {
  void notifyTask(RecoveryTask task);
}
