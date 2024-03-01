package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;
import com.github.j5ik2o.event.store.adapter.java.AggregateId;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class GroupChatId implements AggregateId {

  private static final String TYPE_NAME = "GroupChat";

  private final Ulid value;

  private GroupChatId(Ulid value) {
    this.value = value;
  }

  @Override
  @Nonnull
  public String getTypeName() {
    return "GroupChat";
  }

  @Override
  @Nonnull
  public String getValue() {
    return value.toString();
  }

  @Override
  @Nonnull
  public String asString() {
    return String.format("%s-%s", getTypeName(), value);
  }

  @Nonnull
  public Ulid toUlid() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GroupChatId that = (GroupChatId) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return "GroupChatId{" + "value=" + value + '}';
  }

  public static GroupChatId of(Ulid value) {
    return new GroupChatId(value);
  }

  public static GroupChatId of(String value) {
    if (value.startsWith(TYPE_NAME + "-")) {
      value = value.substring((TYPE_NAME + "-").length());
    }
    return new GroupChatId(Ulid.from(value));
  }

  public static GroupChatId generate() {
    return GroupChatId.of(UlidCreator.getMonotonicUlid());
  }
}
