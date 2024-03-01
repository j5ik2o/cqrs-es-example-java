package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import java.time.LocalDateTime;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class Message {
  private final MessageId id;
  private final String content;

  private final UserAccountId senderId;

  private final LocalDateTime createdAt;

  private Message(MessageId id, String content, UserAccountId senderId, LocalDateTime createdAt) {
    this.id = id;
    this.content = content;
    this.senderId = senderId;
    this.createdAt = createdAt;
  }

  @Nonnull
  public MessageId getId() {
    return id;
  }

  @Nonnull
  public String getContent() {
    return content;
  }

  @Nonnull
  public UserAccountId getSenderId() {
    return senderId;
  }

  @Nonnull
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Message message = (Message) o;
    return Objects.equals(id, message.id)
        && Objects.equals(content, message.content)
        && Objects.equals(senderId, message.senderId)
        && Objects.equals(createdAt, message.createdAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, content, senderId, createdAt);
  }

  @Override
  public String toString() {
    return "Message{"
        + "id="
        + id
        + ", content='"
        + content
        + '\''
        + ", senderId="
        + senderId
        + ", createdAt="
        + createdAt
        + '}';
  }

  public static Message of(
      MessageId id, String content, UserAccountId senderId, LocalDateTime createdAt) {
    return new Message(id, content, senderId, createdAt);
  }
}
