package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/** Checks the values operators rely on in the CloudFormation deployment asset. */
class RmuDeploymentTemplateTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String REPORT_BATCH_ITEM_FAILURES = "ReportBatchItemFailures";

  @Test
  void configuresTheHeadStreamEventSourceMapping() {
    var mapping = template().at("/Resources/RmuEventSourceMapping/Properties");

    assertTrue(mapping.path("BisectBatchOnFunctionError").asBoolean());
    assertTrue(containsText(mapping.path("FunctionResponseTypes"), REPORT_BATCH_ITEM_FAILURES));
    assertEquals("TRIM_HORIZON", mapping.path("StartingPosition").asText());
    assertTrue(mapping.path("BatchSize").isObject());
    assertTrue(mapping.path("MaximumRetryAttempts").isObject());
    assertEquals(
        "OnFailureQueue",
        mapping.at("/DestinationConfig/OnFailure/Destination/Fn::GetAtt/0").asText());
    assertEquals(
        "Arn", mapping.at("/DestinationConfig/OnFailure/Destination/Fn::GetAtt/1").asText());
  }

  @Test
  void setsAFunctionTimeoutThatExceedsTheRetryBudget() {
    var template = template();
    assertEquals(
        "RmuTimeoutSeconds", template.at("/Resources/RmuFunction/Properties/Timeout/Ref").asText());

    var timeoutSeconds = template.at("/Parameters/RmuTimeoutSeconds/Default").asLong();
    var retryElapsedMillis = template.at("/Parameters/RetryMaxElapsedMillis/Default").asLong();

    assertTrue(timeoutSeconds > retryElapsedMillis / 1000);
    assertTrue(timeoutSeconds <= 900);
  }

  @Test
  void keepsTheRecoveryQueueVisibilityTimeoutAboveTheWorkerTimeout() {
    var template = template();
    var visibilityTimeout =
        template.at("/Parameters/RecoveryQueueVisibilityTimeoutSeconds/Default").asLong();
    var workerTimeout = template.at("/Parameters/RecoveryWorkerTimeoutSeconds/Default").asLong();

    assertTrue(visibilityTimeout >= workerTimeout);
  }

  @Test
  void parksExhaustedRecordsAndRecoveryMessagesInDeadLetterQueues() {
    assertEquals(
        "OnFailureDlq",
        template()
            .at(
                "/Resources/OnFailureQueue/Properties/RedrivePolicy/deadLetterTargetArn/Fn::GetAtt/0")
            .asText());
    assertEquals(
        "RecoveryDlq",
        template()
            .at(
                "/Resources/RecoveryQueue/Properties/RedrivePolicy/deadLetterTargetArn/Fn::GetAtt/0")
            .asText());
  }

  @Test
  void wiresTheRecoveryQueueToTheNotifierAndTheWorker() {
    var template = template();

    assertEquals(
        "RecoveryQueue",
        template
            .at(
                "/Resources/RmuFunction/Properties/Environment/Variables/APP_RECOVERY_QUEUE_URL/Ref")
            .asText());
    assertEquals(
        REPORT_BATCH_ITEM_FAILURES,
        template
            .at("/Resources/RecoveryWorkerEventSourceMapping/Properties/FunctionResponseTypes/0")
            .asText());
    assertEquals(
        1, template.at("/Resources/RecoveryWorkerEventSourceMapping/Properties/BatchSize").asInt());
  }

  @Test
  void namesHandlersThatExist() throws Exception {
    assertHandler(
        "/Resources/RmuFunction/Properties/Handler", ReadModelUpdater.class, DynamodbEvent.class);
    assertHandler(
        "/Resources/RecoveryWorkerFunction/Properties/Handler",
        RecoveryWorker.class,
        SQSEvent.class);
  }

  @Test
  void keepsTheEnvironmentRetryDefaultsInStepWithTheRuntimePolicy() {
    var parameters = template().path("Parameters");
    var policy = RetryPolicy.defaults();

    assertEquals(policy.maxAttempts(), parameters.at("/RetryMaxAttempts/Default").asInt());
    assertEquals(
        policy.maxElapsed().toMillis(), parameters.at("/RetryMaxElapsedMillis/Default").asLong());
    assertEquals(
        policy.initialDelay().toMillis(),
        parameters.at("/RetryInitialDelayMillis/Default").asLong());
    assertEquals(
        policy.maxDelay().toMillis(), parameters.at("/RetryMaxDelayMillis/Default").asLong());
  }

  private static void assertHandler(String pointer, Class<?> type, Class<?> eventType)
      throws Exception {
    assertEquals(type.getName() + "::handleRequest", template().at(pointer).asText());
    type.getConstructor();
    type.getMethod("handleRequest", eventType, Context.class);
  }

  private static boolean containsText(JsonNode array, String value) {
    for (var element : array) {
      if (value.equals(element.asText())) {
        return true;
      }
    }
    return false;
  }

  private static JsonNode template() {
    try {
      return MAPPER.readTree(repositoryFile("deploy/rmu-deployment.template.json").toFile());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Path repositoryFile(String relative) {
    var current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    while (current != null) {
      if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
        return current.resolve(relative);
      }
      current = current.getParent();
    }
    throw new IllegalStateException(
        "repository root not found from " + System.getProperty("user.dir"));
  }
}
