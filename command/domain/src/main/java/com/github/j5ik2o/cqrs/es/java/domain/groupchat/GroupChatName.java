package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vavr.control.Either;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class GroupChatName {
  @JsonProperty("value")
  private final String value;

  private GroupChatName(@Nonnull @JsonProperty("value") String value) {
    this.value = value;
  }

  public String asString() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GroupChatName that = (GroupChatName) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return "GroupChatName{" + "value='" + value + '\'' + '}';
  }

  public static GroupChatName of(String value) {
    if (value == null || value.isEmpty()) {
      throw new IllegalArgumentException("name is empty");
    }
    if (value.length() > 255) {
      throw new IllegalArgumentException("name is too long");
    }
    return new GroupChatName(value);
  }

  public static Either<IllegalArgumentException, GroupChatName> validate(String name) {
    try {
      return Either.right(of(name));
    } catch (IllegalArgumentException e) {
      return Either.left(e);
    }
  }
}
