package com.github.j5ik2o.cqrs.es.java.main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("write")
public class WriteAPIMain implements CommandLineRunner {
  static final Logger LOGGER = LoggerFactory.getLogger(WriteAPIMain.class);
  private AppConfig appConfig;

  @Override
  public void run(String... args) throws Exception {}
}
