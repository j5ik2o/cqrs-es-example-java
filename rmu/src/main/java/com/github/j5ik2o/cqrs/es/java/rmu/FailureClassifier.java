package com.github.j5ik2o.cqrs.es.java.rmu;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

/**
 * Splits failures into permanent data problems and retryable storage problems.
 *
 * <p>A permanent failure is written to the recovery task store instead of being retried forever. A
 * retryable failure (a database outage, network error, or throttling response) is retried with
 * backoff. An unknown failure is treated as retryable a bounded number of times; if it still fails
 * it is parked as a recovery task.
 */
public final class FailureClassifier {

  private static final Set<Integer> PERMANENT_MYSQL_ERRORS = Set.of(1062, 1264, 1366, 1406, 1452);

  private static final Set<String> RETRYABLE_AWS_ERROR_CODES =
      Set.of(
          "ThrottlingException",
          "Throttling",
          "ProvisionedThroughputExceededException",
          "RequestLimitExceeded",
          "InternalServerError",
          "ServiceUnavailable",
          "ExpiredIteratorException",
          "TransactionConflictException",
          "RequestTimeout",
          "RequestTimeoutException");

  private FailureClassifier() {}

  /** Returns the failure kind when retrying cannot fix the record. */
  public static Optional<FailureKind> classify(Throwable failure) {
    for (var cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof PermanentFailure permanentFailure) {
        return Optional.of(permanentFailure.kind());
      }
    }
    for (var cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sqlException
          && PERMANENT_MYSQL_ERRORS.contains(sqlException.getErrorCode())) {
        return Optional.of(FailureKind.READ_MODEL_CONSTRAINT);
      }
    }
    return Optional.empty();
  }

  /** Returns whether the same operation may succeed later. */
  public static boolean isRetryable(Throwable failure) {
    if (failure == null) {
      return false;
    }
    if (classify(failure).isPresent()) {
      return false;
    }
    if (failure instanceof InterruptedIOException) {
      return false;
    }
    // An interrupt wrapped by the AWS SDK or another layer is still an interrupt, not a storage
    // outage. Stop retrying and let the interruption propagate.
    for (var cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof InterruptedException
          || cause instanceof CancellationException
          || cause instanceof RetryInterruptedException) {
        return false;
      }
    }
    for (var cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sqlException) {
        // Permanent codes are already excluded by classify(), so any other SQL state is treated
        // as a storage outage and retried.
        return true;
      }
      if (cause instanceof AwsServiceException sdkException) {
        var details = sdkException.awsErrorDetails();
        if (details != null && RETRYABLE_AWS_ERROR_CODES.contains(details.errorCode())) {
          return true;
        }
        var statusCode = sdkException.statusCode();
        if (statusCode == 429 || statusCode >= 500) {
          return true;
        }
      }
      if (cause instanceof IOException || cause instanceof TimeoutException) {
        return true;
      }
    }
    // Unknown storage errors are retried a bounded number of times. A record that still fails is
    // parked as a recovery task by the caller.
    return true;
  }
}
