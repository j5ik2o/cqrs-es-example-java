package com.github.j5ik2o.cqrs.es.java.main;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
@ToString
public class AppConfig {

  private DynamoDb dynamoDb;
  private Stream stream;
  private Persistence persistence;

  public boolean hasDynamoDbConfig() {
    return dynamoDb != null && dynamoDb.hasConfig();
  }

  @Getter
  @Setter
  @ToString
  public static class DynamoDb {
    private String region;

    private String endpointUrl;

    private String accessKey;

    private String secretAccessKey;

    public boolean hasConfig() {
      return region != null && endpointUrl != null && accessKey != null && secretAccessKey != null;
    }
  }

  @Getter
  @Setter
  @ToString
  public static class Stream {
    private String headTableName;
    private String journalTableName;
    private int maxItemCount;
    private int maxEventsPerTransaction = 1;
    private int retryMaxAttempts = 5;
    private long retryMaxElapsedMillis = 30_000L;
    private long retryInitialDelayMillis = 200L;
    private long retryMaxDelayMillis = 5_000L;
    private long pollBackoffInitialMillis = 500L;
    private long pollBackoffMaxMillis = 30_000L;
  }

  @Getter
  @Setter
  @ToString
  public static class Persistence {
    private String journalTableName;
    private String snapshotTableName;
    private String headTableName;
    private String snapshotAidIndexName;
    private long snapshotInterval;
  }
}
