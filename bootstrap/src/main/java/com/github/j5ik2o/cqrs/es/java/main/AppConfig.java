package com.github.j5ik2o.cqrs.es.java.main;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("app")
@Getter
@Setter
@ToString
public class AppConfig {

  private String journalTableName;

  private DynamoDbConfig dynamoDbConfig;


}
