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
    private String journalTableName;
    private int maxItemCount;
  }
}
