package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import io.vavr.control.Either;
import java.time.LocalDateTime;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class Message {
  private final MessageId id;
  private final String content;

  private final UserAccountId senderId;

  private final LocalDateTime createdAt;

  private Message(
      @Nonnull @JsonProperty("id") MessageId id,
      @Nonnull @JsonProperty("content") String content,
      @Nonnull @JsonProperty("senderId") UserAccountId senderId,
      @Nonnull @JsonProperty("createdAt") LocalDateTime createdAt) {
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
    if (content == null || content.isEmpty()) {
      throw new IllegalArgumentException("content is empty");
    }
    if (content.length() > 1000) {
      throw new IllegalArgumentException("content is too long");
    }
    return new Message(id, content, senderId, createdAt);
  }

  public static Either<IllegalArgumentException, Message> validate(
      MessageId id, String content, UserAccountId senderId, LocalDateTime createdAt) {
    try {
      return Either.right(of(id, content, senderId, createdAt));
    } catch (IllegalArgumentException e) {
      return Either.left(e);
    }
  }
}
