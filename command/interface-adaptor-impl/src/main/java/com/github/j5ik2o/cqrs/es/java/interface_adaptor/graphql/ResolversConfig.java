package com.github.j5ik2o.cqrs.es.java.interface_adaptor.graphql;

import com.github.j5ik2o.cqrs.es.java.processor.GroupChatCommandProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("WriteAPI")
public class ResolversConfig {
  @Bean
  public MutationResolver mutationResolver(GroupChatCommandProcessor groupChatCommandProcessor) {
    return new MutationResolverImpl(groupChatCommandProcessor);
  }

  @Bean
  public QueryResolver queryResolver() {
    return new QueryResolverImpl();
  }
}
