package com.github.j5ik2o.cqrs.es.java.rmu;

import java.util.List;

/**
 * The partial-batch response for a DynamoDB Streams event source mapping.
 *
 * <p>The event source mapping must enable {@code ReportBatchItemFailures} and {@code
 * BisectBatchOnFunctionError}; otherwise Lambda retries the whole batch.
 */
public record DynamodbBatchResponse(List<BatchItemFailure> batchItemFailures) {
  public static DynamodbBatchResponse empty() {
    return new DynamodbBatchResponse(List.of());
  }

  public record BatchItemFailure(String itemIdentifier) {}
}
