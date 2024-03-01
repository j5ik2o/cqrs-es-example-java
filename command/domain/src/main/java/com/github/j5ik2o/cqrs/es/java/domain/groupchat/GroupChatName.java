package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

public final class GroupChatName {

  private final String value;

  private GroupChatName(String value) {
    this.value = value;
  }

  public String asString() {
    return value;
  }

  public static GroupChatName of(String value) {
    return new GroupChatName(value);
  }
}
