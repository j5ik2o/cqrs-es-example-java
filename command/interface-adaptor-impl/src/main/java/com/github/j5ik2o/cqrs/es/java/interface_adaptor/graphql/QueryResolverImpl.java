package com.github.j5ik2o.cqrs.es.java.interface_adaptor.graphql;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class QueryResolverImpl implements QueryResolver {
  @Override
  public Mono<String> healthCheck() throws Exception {
    return Mono.just("OK");
  }
}
