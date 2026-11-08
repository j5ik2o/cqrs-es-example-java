package com.github.j5ik2o.cqrs.es.java.rmu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Wakes the recovery worker through SQS.
 *
 * <p>The message carries identifiers only. The durable task in MySQL stays the source of truth and
 * the worker re-reads the journal, so a lost or duplicated message changes nothing.
 */
public final class SqsRecoveryNotifier implements RecoveryNotifier {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Seam for tests; production sends through {@link SqsClient}. */
  @FunctionalInterface
  interface MessageSender {
    void send(String queueUrl, String body);
  }

  private final MessageSender sender;
  private final String queueUrl;

  public SqsRecoveryNotifier(SqsClient sqsClient, String queueUrl) {
    this(
        (url, body) ->
            sqsClient.sendMessage(
                SendMessageRequest.builder().queueUrl(url).messageBody(body).build()),
        queueUrl);
  }

  SqsRecoveryNotifier(MessageSender sender, String queueUrl) {
    this.sender = sender;
    this.queueUrl = queueUrl;
  }

  @Override
  public void notifyTask(RecoveryTask task) {
    sender.send(queueUrl, messageBody(task));
  }

  static String messageBody(RecoveryTask task) {
    var notification =
        new Notification(
            task.storeId(),
            task.projectionName(),
            task.projectionVersion(),
            task.aggregateId(),
            task.seqNr(),
            task.failureKind().asString());
    try {
      return MAPPER.writeValueAsString(notification);
    } catch (JsonProcessingException e) {
      throw new RecoveryException("failed to serialize a recovery notification", e);
    }
  }

  private record Notification(
      String storeId,
      String projectionName,
      String projectionVersion,
      String aggregateId,
      long seqNr,
      String failureType) {}
}
