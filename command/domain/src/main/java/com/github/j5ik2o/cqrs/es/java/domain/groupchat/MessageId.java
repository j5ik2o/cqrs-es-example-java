package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;
import io.vavr.control.Either;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class MessageId {
  @JsonProperty("value")
  private final Ulid value;

  private MessageId(@Nonnull @JsonProperty("value") Ulid value) {
    this.value = value;
  }

  public String asString() {
    return value.toString();
  }

  @Nonnull
  public Ulid toUlid() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MessageId messageId = (MessageId) o;
    return Objects.equals(value, messageId.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return "MessageId{" + "value=" + value + '}';
  }

  public static MessageId of(Ulid value) {
    return new MessageId(value);
  }

  public static MessageId of(String value) {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException("value is empty");
    }
    return new MessageId(Ulid.from(value));
  }

  public static MessageId generate() {
    return new MessageId(UlidCreator.getMonotonicUlid());
  }

  public static Either<IllegalArgumentException, MessageId> validate(String value) {
    try {
      return Either.right(of(value));
    } catch (IllegalArgumentException e) {
      return Either.left(e);
    }
  }
}
