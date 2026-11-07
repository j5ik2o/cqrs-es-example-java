package com.github.j5ik2o.cqrs.es.java.rmu;

import java.util.Locale;

/**
 * Why an event could not be applied. The kind is stored with a recovery task so an operator can
 * tell a data problem from a storage outage.
 */
public enum FailureKind {
  EVENT_PAYLOAD,
  UNSUPPORTED_EVENT,
  JOURNAL_GAP,
  READ_MODEL_CONSTRAINT,
  RECOVERY_TASK_STORE,
  UNKNOWN;

  public String asString() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static FailureKind fromString(String value) {
    if (value == null || value.isBlank()) {
      return UNKNOWN;
    }
    try {
      return valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return UNKNOWN;
    }
  }
}
