package com.github.j5ik2o.cqrs.es.java.main;

import java.net.URI;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsAsyncClient;
import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

@Getter
@Setter
@ToString
@Configuration
@Profile("write")
public class DynamoDbConfig {

  private AppConfig appConfig;

  @Bean
  public DynamoDbClient dynamoDbClient() {
    if (appConfig.hasDynamoDbConfig()) {
      return DynamoDbClient.builder()
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
}
