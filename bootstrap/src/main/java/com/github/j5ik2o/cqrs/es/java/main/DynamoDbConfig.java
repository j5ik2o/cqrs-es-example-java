package com.github.j5ik2o.cqrs.es.java.main;

import com.github.j5ik2o.cqrs.es.java.rmu.DynamoDBJournalReader;
import com.github.j5ik2o.cqrs.es.java.rmu.JournalReader;
import java.net.URI;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

@Configuration
@Getter
@Setter
@ToString
public class DynamoDbConfig {

  private final AppConfig appConfig;

  public DynamoDbConfig(AppConfig appConfig) {
    this.appConfig = appConfig;
  }

  @Bean
  public DynamoDbClient dynamoDbClient() {
    if (appConfig.hasDynamoDbConfig()) {
      return DynamoDbClient.builder()
          .region(Region.of(appConfig.getDynamoDb().getRegion()))
          .endpointOverride(URI.create(appConfig.getDynamoDb().getEndpointUrl()))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(
                      appConfig.getDynamoDb().getAccessKey(),
                      appConfig.getDynamoDb().getSecretAccessKey())))
          .build();
    } else {
      return DynamoDbClient.create();
    }
  }

  @Bean
  public DynamoDbAsyncClient dynamoDbAsyncClient() {
    if (appConfig.hasDynamoDbConfig()) {
      return DynamoDbAsyncClient.builder()
          .region(Region.of(appConfig.getDynamoDb().getRegion()))
          .endpointOverride(URI.create(appConfig.getDynamoDb().getEndpointUrl()))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(
                      appConfig.getDynamoDb().getAccessKey(),
                      appConfig.getDynamoDb().getSecretAccessKey())))
          .build();
    } else {
      return DynamoDbAsyncClient.create();
    }
  }

  @Bean
  public DynamoDbStreamsClient dynamoDbStreamsClient() {
    if (appConfig.hasDynamoDbConfig()) {
      return DynamoDbStreamsClient.builder()
          .region(Region.of(appConfig.getDynamoDb().getRegion()))
          .endpointOverride(URI.create(appConfig.getDynamoDb().getEndpointUrl()))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(
                      appConfig.getDynamoDb().getAccessKey(),
                      appConfig.getDynamoDb().getSecretAccessKey())))
          .build();
    } else {
      return DynamoDbStreamsClient.create();
    }
  }

  @Bean
  public DynamoDbStreamsAsyncClient dynamoDbStreamsAsyncClient() {
    if (appConfig.hasDynamoDbConfig()) {
      return DynamoDbStreamsAsyncClient.builder()
          .region(Region.of(appConfig.getDynamoDb().getRegion()))
          .endpointOverride(URI.create(appConfig.getDynamoDb().getEndpointUrl()))
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(
                      appConfig.getDynamoDb().getAccessKey(),
                      appConfig.getDynamoDb().getSecretAccessKey())))
          .build();
    } else {
      return DynamoDbStreamsAsyncClient.create();
    }
  }

  @Bean
  @Profile("local-rmu")
  public JournalReader journalReader(DynamoDbClient dynamoDbClient) {
    return new DynamoDBJournalReader(dynamoDbClient, appConfig.getStream().getJournalTableName());
  }
}
