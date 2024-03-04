package com.github.j5ik2o.cqrs.es.java.interface_adaptor.repository;

import com.github.j5ik2o.cqrs.es.java.domain.groupchat.*;
import com.github.j5ik2o.event.store.adapter.java.EventStoreAsync;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import org.springframework.stereotype.Repository;

@Repository
public final class GroupChatRepositoryImpl implements GroupChatRepository {
  private final EventStoreAsync<GroupChatId, GroupChat, GroupChatEvent> eventStore;
  private final Option<BiPredicate<GroupChatEvent, GroupChat>> snapshotPredicateOpt;

  private GroupChatRepositoryImpl(
      EventStoreAsync<GroupChatId, GroupChat, GroupChatEvent> eventStore,
      Option<BiPredicate<GroupChatEvent, GroupChat>> snapshotPredicateOpt) {
    this.eventStore = eventStore;
    this.snapshotPredicateOpt = snapshotPredicateOpt;
  }

  @Override
  public GroupChatRepository withSnapshotPredicate(
      Option<BiPredicate<GroupChatEvent, GroupChat>> predicate) {
    return new GroupChatRepositoryImpl(eventStore, predicate);
  }

  @Override
  public GroupChatRepository withRetentionCriteriaOf(long numberOfEvents) {
    return withSnapshotPredicate(retentionCriteriaOf(numberOfEvents));
  }

  @Override
  public CompletableFuture<Option<GroupChat>> findById(GroupChatId id) {
    return eventStore
        .getLatestSnapshotById(GroupChat.class, id)
        .thenCompose(
            result -> {
              if (result.isEmpty()) {
                return CompletableFuture.completedFuture(Option.none());
              } else {
                return eventStore
                    .getEventsByIdSinceSequenceNumber(
                        GroupChatEvent.class, id, result.get().getSequenceNumber() + 1)
                    .thenApply(
                        events -> Option.some(GroupChat.replay(Vector.ofAll(events), result.get())))
                    .exceptionallyCompose(
                        e ->
                            CompletableFuture.failedFuture(
                                new RepositoryException("occurred an error", e)));
              }
            })
        .exceptionallyCompose(
            e -> CompletableFuture.failedFuture(new RepositoryException("occurred an error", e)));
  }

  @Override
  public CompletableFuture<Void> store(GroupChatEvent event, GroupChat aggregate) {
    if (event.isCreated()
        || snapshotPredicateOpt.fold(() -> false, (p) -> p.test(event, aggregate))) {
      return storeEventAndSnapshot(event, aggregate);
    } else {
      return storeEvent(event, aggregate.getVersion());
    }
  }

  public CompletableFuture<Void> storeEventAndSnapshot(GroupChatEvent event, GroupChat aggregate) {
    return eventStore.persistEventAndSnapshot(event, aggregate);
  }

  public CompletableFuture<Void> storeEvent(GroupChatEvent event, long version) {
    return eventStore.persistEvent(event, version);
  }

  public static Option<BiPredicate<GroupChatEvent, GroupChat>> retentionCriteriaOf(
      long numberOfEvents) {
    return Option.some((event, ignore) -> event.sequenceNumber() % numberOfEvents == 0);
  }

  public static GroupChatRepositoryImpl of(
      EventStoreAsync<GroupChatId, GroupChat, GroupChatEvent> eventStore) {
    return new GroupChatRepositoryImpl(eventStore, Option.none());
  }

  public static GroupChatRepositoryImpl of(
      EventStoreAsync<GroupChatId, GroupChat, GroupChatEvent> eventStore,
      Option<BiPredicate<GroupChatEvent, GroupChat>> snapshotPredicateOpt) {
    return new GroupChatRepositoryImpl(eventStore, snapshotPredicateOpt);
  }
}
