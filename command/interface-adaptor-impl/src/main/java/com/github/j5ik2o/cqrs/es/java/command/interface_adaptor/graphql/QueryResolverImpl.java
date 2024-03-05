package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.graphql;

import org.springframework.context.annotation.Profile;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
@Profile("write")
public class QueryResolverImpl implements QueryResolver {
  @QueryMapping
  @Override
  public Mono<String> healthCheck() throws Exception {
    return Mono.just("OK");
  }
}
