package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository;

import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatEvent;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatId;
import com.github.j5ik2o.event.store.adapter.java.EventStoreAsync;
import io.vavr.control.Option;
import java.util.function.BiPredicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("write")
public class RepositoryConfig {

  @Value("${app.persistence.snapshot-interval}")
  private long snapshotInterval;

  @Bean
  public Option<BiPredicate<GroupChatEvent, GroupChat>> snapshotPredicateOpt() {
    return GroupChatRepositoryImpl.retentionCriteriaOf(snapshotInterval);
  }

  @Bean
  public GroupChatRepositoryImpl groupChatRepository(
      EventStoreAsync<GroupChatId, GroupChat, GroupChatEvent> eventStoreAsync,
      Option<BiPredicate<GroupChatEvent, GroupChat>> snapshotPredicateOpt) {
    return GroupChatRepositoryImpl.of(eventStoreAsync, snapshotPredicateOpt);
  }
}
