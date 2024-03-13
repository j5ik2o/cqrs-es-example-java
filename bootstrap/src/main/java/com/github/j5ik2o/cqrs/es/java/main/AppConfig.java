package com.github.j5ik2o.cqrs.es.java.main;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("app")
public class AppConfig {

  private String journalTableName;

  public String getJournalTableName() {
    return journalTableName;
  }

  public void setJournalTableName(String journalTableName) {
    this.journalTableName = journalTableName;
  }
}
