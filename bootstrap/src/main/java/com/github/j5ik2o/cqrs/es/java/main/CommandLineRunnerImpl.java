package com.github.j5ik2o.cqrs.es.java.main;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-rmu")
public class CommandLineRunnerImpl implements CommandLineRunner {
  @Override
  public void run(String... args) throws Exception {}
}
