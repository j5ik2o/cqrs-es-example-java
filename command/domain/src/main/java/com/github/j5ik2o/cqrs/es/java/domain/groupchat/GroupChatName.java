package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    return new GroupChatName(value);
  }
}
