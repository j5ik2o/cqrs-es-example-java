package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class FailureClassifierTest {

  @Test
  void classifiesAReadModelConstraintAsPermanent() {
    var failure = new RuntimeException(new SQLException("foreign key", "23000", 1452));

    assertEquals(
        Optional.of(FailureKind.READ_MODEL_CONSTRAINT), FailureClassifier.classify(failure));
    assertFalse(FailureClassifier.isRetryable(failure));
  }

  @Test
  void classifiesAStorageOutageAsRetryable() {
    var failure = new RuntimeException(new SQLException("connection reset", "08S01", 0));

    assertTrue(FailureClassifier.classify(failure).isEmpty());
    assertTrue(FailureClassifier.isRetryable(failure));
  }

  @Test
  void aPayloadFailureStaysPermanent() {
    var failure = new PermanentFailure(FailureKind.EVENT_PAYLOAD, "not json");

    assertEquals(Optional.of(FailureKind.EVENT_PAYLOAD), FailureClassifier.classify(failure));
    assertFalse(FailureClassifier.isRetryable(failure));
  }

  @Test
  void doesNotRetryAnInterruptWrappedByAnotherLayer() {
    var failure = new RuntimeException(new InterruptedException("stopped"));

    assertFalse(FailureClassifier.isRetryable(failure));
  }

  @Test
  void doesNotRetryACancellationWrappedByAnotherLayer() {
    var failure = new RuntimeException(new CancellationException("stopped"));

    assertFalse(FailureClassifier.isRetryable(failure));
  }
}
