package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository;

import com.github.j5ik2o.cqrs.es.java.domain.groupchat.*;
import com.github.j5ik2o.event.store.adapter.java.core.AggregateId;
import com.github.j5ik2o.event.store.adapter.java.core.AsyncEventStore;
import com.github.j5ik2o.event.store.adapter.java.core.EventEnvelope;
import com.github.j5ik2o.event.store.adapter.java.core.SnapshotEnvelope;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;

public class GroupChatRepositoryImpl implements GroupChatRepository {
  private final AsyncEventStore<GroupChatEvent, GroupChat> eventStore;
  private final Option<BiPredicate<GroupChatEvent, GroupChat>> snapshotPredicateOpt;

  private GroupChatRepositoryImpl(
      AsyncEventStore<GroupChatEvent, GroupChat> eventStore,
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
    var aggregateId = toAggregateId(id);
    return eventStore
        .getLatestSnapshotById(aggregateId)
        .thenCompose(
            result -> {
              if (result.isEmpty()) {
                return CompletableFuture.completedFuture(Option.none());
              }
              var snapshotOpt = result.get().snapshot();
              if (snapshotOpt.isEmpty()) {
                return CompletableFuture.completedFuture(Option.none());
              }
              var snapshot = snapshotOpt.get();
              return eventStore
                  .getEventsByIdSinceSeqNr(aggregateId, snapshot.seqNr() + 1)
                  .thenApply(
                      events ->
                          Option.some(
                              GroupChat.replay(
                                  Vector.ofAll(events).map(EventEnvelope::payload),
                                  snapshot.aggregate())))
                  .exceptionallyCompose(
                      e ->
                          CompletableFuture.failedFuture(
                              new RepositoryException("occurred an error", e)));
            })
        .exceptionallyCompose(
            e -> CompletableFuture.failedFuture(new RepositoryException("occurred an error", e)));
  }

  @Override
  public CompletableFuture<Void> store(GroupChatEvent event, GroupChat aggregate) {
    if (event.isCreated()
        || snapshotPredicateOpt.fold(() -> false, p -> p.test(event, aggregate))) {
      return storeEventAndSnapshot(event, aggregate);
    } else {
      return storeEvent(event);
    }
  }

  public CompletableFuture<Void> storeEventAndSnapshot(GroupChatEvent event, GroupChat aggregate) {
    return eventStore.persistEventAndSnapshot(
        toEventEnvelope(event), toSnapshotEnvelope(event, aggregate));
  }

  public CompletableFuture<Void> storeEvent(GroupChatEvent event) {
    return eventStore.persistEvent(toEventEnvelope(event));
  }

  private static AggregateId toAggregateId(GroupChatId id) {
    return AggregateId.of(id.getTypeName(), id.getValue());
  }

  private static EventEnvelope<GroupChatEvent> toEventEnvelope(GroupChatEvent event) {
    return EventEnvelope.<GroupChatEvent>builder()
        .aggregateId(toAggregateId(event.aggregateId()))
        .seqNr(event.sequenceNumber())
        .occurredAt(event.occurredAt())
        .payload(event)
        .build();
  }

  private static SnapshotEnvelope<GroupChat> toSnapshotEnvelope(
      GroupChatEvent event, GroupChat aggregate) {
    return SnapshotEnvelope.<GroupChat>builder()
        .aggregate(aggregate)
        .seqNr(event.sequenceNumber())
        .build();
  }

  public static Option<BiPredicate<GroupChatEvent, GroupChat>> retentionCriteriaOf(
      long numberOfEvents) {
    return Option.some((event, ignore) -> event.sequenceNumber() % numberOfEvents == 0);
  }

  public static GroupChatRepositoryImpl of(AsyncEventStore<GroupChatEvent, GroupChat> eventStore) {
    return new GroupChatRepositoryImpl(eventStore, Option.none());
  }

  public static GroupChatRepositoryImpl of(
      AsyncEventStore<GroupChatEvent, GroupChat> eventStore,
      Option<BiPredicate<GroupChatEvent, GroupChat>> snapshotPredicateOpt) {
    return new GroupChatRepositoryImpl(eventStore, snapshotPredicateOpt);
  }
}
