package com.github.j5ik2o.cqrs.es.java.rmu;

import java.util.List;

/**
 * The partial-batch response for a DynamoDB Streams event source mapping.
 *
 * <p>The event source mapping must enable {@code ReportBatchItemFailures} to process this response.
 * Otherwise Lambda ignores the failure list. {@code BisectBatchOnFunctionError} is a separate
 * setting that controls batch splitting.
 */
public record DynamodbBatchResponse(List<BatchItemFailure> batchItemFailures) {
  public static DynamodbBatchResponse empty() {
    return new DynamodbBatchResponse(List.of());
  }

  public record BatchItemFailure(String itemIdentifier) {}
}
