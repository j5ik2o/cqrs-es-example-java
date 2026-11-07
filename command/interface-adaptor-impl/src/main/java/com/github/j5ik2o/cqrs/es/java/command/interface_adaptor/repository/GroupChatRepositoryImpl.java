package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository;

import com.github.j5ik2o.cqrs.es.java.domain.groupchat.*;
import com.github.j5ik2o.event.store.adapter.java.core.AggregateId;
import com.github.j5ik2o.event.store.adapter.java.core.AsyncEventStore;
import com.github.j5ik2o.event.store.adapter.java.core.EventEnvelope;
import com.github.j5ik2o.event.store.adapter.java.core.SnapshotEnvelope;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.List;
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
              var since = snapshotOpt.map(snapshot -> snapshot.seqNr() + 1).orElse(1L);
              var base = snapshotOpt.map(snapshot -> snapshot.aggregate()).orElse(null);
              return eventStore
                  .getEventsByIdSinceSeqNr(aggregateId, since)
                  .thenApply(events -> replay(events, base))
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

  private static Option<GroupChat> replay(
      List<EventEnvelope<GroupChatEvent>> events, GroupChat base) {
    if (base != null) {
      return Option.some(GroupChat.replay(Vector.ofAll(events).map(EventEnvelope::payload), base));
    }
    if (events.isEmpty()) {
      return Option.none();
    }
    if (!(events.get(0).payload() instanceof GroupChatEvent.Created created)) {
      throw new IllegalStateException(
          "the first event of an aggregate without a snapshot must be Created");
    }
    var initial =
        GroupChat.from(
            created.aggregateId(),
            false,
            created.name(),
            created.members(),
            Messages.ofEmpty(),
            created.sequenceNumber(),
            1);
    var rest = Vector.ofAll(events.subList(1, events.size())).map(EventEnvelope::payload);
    return Option.some(GroupChat.replay(rest, initial));
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
