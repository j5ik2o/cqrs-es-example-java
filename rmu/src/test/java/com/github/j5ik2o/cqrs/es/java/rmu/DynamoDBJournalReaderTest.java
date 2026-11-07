package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

@Testcontainers
class DynamoDBJournalReaderTest {

  private static final String JOURNAL_TABLE_NAME = "journal";
  private static final String AGGREGATE_ID = "GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z";
  private static final String OTHER_AGGREGATE_ID = "GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Y";

  @Container
  private static final LocalStackContainer LOCALSTACK =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14.0"))
          .withServices(DYNAMODB);

  private static DynamoDbClient client;

  @BeforeAll
  static void setUp() {
    client =
        DynamoDbClient.builder()
            .endpointOverride(LOCALSTACK.getEndpoint())
            .region(Region.of(LOCALSTACK.getRegion()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
            .build();
    client.createTable(
        CreateTableRequest.builder()
            .tableName(JOURNAL_TABLE_NAME)
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName("aid")
                    .attributeType(ScalarAttributeType.S)
                    .build(),
                AttributeDefinition.builder()
                    .attributeName("seq_nr")
                    .attributeType(ScalarAttributeType.N)
                    .build())
            .keySchema(
                KeySchemaElement.builder().attributeName("aid").keyType(KeyType.HASH).build(),
                KeySchemaElement.builder().attributeName("seq_nr").keyType(KeyType.RANGE).build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build());
    putEvent(AGGREGATE_ID, 1, "GroupChatCreated@1", "one", 1_000L);
    putEvent(AGGREGATE_ID, 2, "GroupChatMemberAdded@1", "two", 2_000L);
    putEvent(AGGREGATE_ID, 3, "GroupChatMessagePosted@1", "three", 3_000L);
    putEvent(OTHER_AGGREGATE_ID, 2, "GroupChatMemberAdded@1", "other", 4_000L);
  }

  @AfterAll
  static void tearDown() {
    if (client != null) {
      client.close();
    }
  }

  @Test
  void readsTheRequestedRangeInAscendingOrder() {
    var reader = new DynamoDBJournalReader(client, JOURNAL_TABLE_NAME);

    var events = reader.readEvents(AGGREGATE_ID, 2, 3);

    assertEquals(List.of(2L, 3L), events.stream().map(HeadEvent::seqNr).toList());
    assertEquals("GroupChatMemberAdded@1", events.get(0).manifest());
    assertEquals(Instant.ofEpochSecond(0, 2_000L), events.get(0).occurredAt());
    assertArrayEquals("two".getBytes(StandardCharsets.UTF_8), events.get(0).payload());
    assertArrayEquals("three".getBytes(StandardCharsets.UTF_8), events.get(1).payload());
  }

  @Test
  void returnsAnEmptyListWhenTheRangeIsEmpty() {
    var reader = new DynamoDBJournalReader(client, JOURNAL_TABLE_NAME);

    assertTrue(reader.readEvents(AGGREGATE_ID, 4, 5).isEmpty());
    assertTrue(reader.readEvents(AGGREGATE_ID, 3, 2).isEmpty());
  }

  private static void putEvent(
      String aggregateId, long seqNr, String manifest, String payload, long occurredAtNanos) {
    client.putItem(
        PutItemRequest.builder()
            .tableName(JOURNAL_TABLE_NAME)
            .item(
                Map.of(
                    "aid", AttributeValue.fromS(aggregateId),
                    "seq_nr", AttributeValue.fromN(Long.toString(seqNr)),
                    "occurred_at", AttributeValue.fromN(Long.toString(occurredAtNanos)),
                    "manifest", AttributeValue.fromS(manifest),
                    "payload",
                        AttributeValue.fromB(
                            software.amazon.awssdk.core.SdkBytes.fromByteBuffer(
                                ByteBuffer.wrap(payload.getBytes(StandardCharsets.UTF_8))))))
            .build());
  }
}
