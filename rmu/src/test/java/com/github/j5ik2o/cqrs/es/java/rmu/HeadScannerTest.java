package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.DYNAMODB;

import java.util.ArrayList;
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
class HeadScannerTest {

  private static final String HEAD_TABLE_NAME = "head";
  private static final String CONFIG_AGGREGATE_ID = "__config__";
  private static final String STORE_ID = "store-0001";

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
            .tableName(HEAD_TABLE_NAME)
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName("aid")
                    .attributeType(ScalarAttributeType.S)
                    .build())
            .keySchema(
                KeySchemaElement.builder().attributeName("aid").keyType(KeyType.HASH).build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build());
    client.putItem(
        PutItemRequest.builder()
            .tableName(HEAD_TABLE_NAME)
            .item(
                Map.of(
                    "aid", AttributeValue.fromS(CONFIG_AGGREGATE_ID),
                    "store_id", AttributeValue.fromS(STORE_ID)))
            .build());
    putHead("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z", 3);
    putHead("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Y", 7);
    putHead("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0X", 11);
  }

  @AfterAll
  static void tearDown() {
    if (client != null) {
      client.close();
    }
  }

  @Test
  void scansEveryPageAndExcludesTheConfigurationItem() {
    var scanner = new DynamoDBHeadScanner(client, HEAD_TABLE_NAME, 1);
    var heads = new ArrayList<Head>();

    scanner.scanHeads(heads::add);

    assertEquals(
        List.of(
            new Head("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0X", "GroupChat", 11),
            new Head("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Y", "GroupChat", 7),
            new Head("GroupChat-01H42K4ABWQ5V2XQEP3A48VE0Z", "GroupChat", 3)),
        heads.stream().sorted(java.util.Comparator.comparing(Head::aggregateId)).toList());
    assertEquals(STORE_ID, scanner.storeId());
  }

  private static void putHead(String aggregateId, long seqNr) {
    client.putItem(
        PutItemRequest.builder()
            .tableName(HEAD_TABLE_NAME)
            .item(
                Map.of(
                    "aid", AttributeValue.fromS(aggregateId),
                    "type_name", AttributeValue.fromS("GroupChat"),
                    "seq_nr", AttributeValue.fromN(Long.toString(seqNr))))
            .build());
  }
}
