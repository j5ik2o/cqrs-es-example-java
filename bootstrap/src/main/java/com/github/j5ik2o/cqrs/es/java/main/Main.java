package com.github.j5ik2o.cqrs.es.java.main;

import org.apache.commons.cli.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;

@SpringBootApplication
@ComponentScan(basePackages = {"com.github.j5ik2o.cqrs.es.java"})
@MapperScan({
  "com.github.j5ik2o.cqrs.es.java.rmu.dao",
  "com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao"
})
public class Main {
  public static void main(String[] args) throws ParseException {

    Options options = new Options();
    options.addOption("w", "write-api", false, "write api");
    options.addOption("r", "read-api", false, "read api");
    options.addOption("l", "local-rmu", false, "local rmu");
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);

    SpringApplication app = new SpringApplication(Main.class);
    ConfigurableEnvironment environment = new StandardEnvironment();
    if (cmd.hasOption("w")) {
      environment.addActiveProfile("write");
    } else if (cmd.hasOption("r")) {
      environment.addActiveProfile("read");
    } else if (cmd.hasOption("l")) {
      environment.addActiveProfile("local-rmu");
    } else {
      throw new IllegalArgumentException("profile is not specified");
    }
    app.setEnvironment(environment);
    app.run(args);
  }
}
