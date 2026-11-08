package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.graphql;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

@Configuration
public class GraphQlScalarConfig {

  @Bean
  public RuntimeWiringConfigurer naiveDateTimeConfigurer() {
    return wiring -> wiring.scalar(NaiveDateTimeScalar.create());
  }
}
