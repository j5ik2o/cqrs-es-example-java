package com.github.j5ik2o.cqrs.es.java.rmu;

/** The aggregate id prefixes the read model updater recognises. */
public final class AggregateType {
  public static final String GROUP_CHAT = "GroupChat";
  public static final String GROUP_CHAT_ID_PREFIX = GROUP_CHAT + "-";

  private AggregateType() {}

  public static boolean isGroupChat(String aggregateId) {
    return aggregateId != null && aggregateId.startsWith(GROUP_CHAT_ID_PREFIX);
  }
}
