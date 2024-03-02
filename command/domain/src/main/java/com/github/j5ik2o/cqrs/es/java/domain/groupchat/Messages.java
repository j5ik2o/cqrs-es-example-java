package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vavr.Tuple2;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.Objects;
import javax.annotation.Nonnull;

public final class Messages {
  @JsonProperty("values")
  private final Vector<Message> values;

  private Messages(@Nonnull @JsonProperty("values") Vector<Message> values) {
    this.values = values;
  }

  public Messages add(Message value) {
    return new Messages(values.append(value));
  }

  public Option<Tuple2<Message, Messages>> removeByMessageId(MessageId messageId) {
    return values
        .find(message -> message.getId().equals(messageId))
        .map(message -> new Tuple2<>(message, new Messages(values.remove(message))));
  }

  public Option<Message> findByMessageId(MessageId messageId) {
    return values.find(message -> message.getId().equals(messageId));
  }

  public boolean containsByMessageId(MessageId messageId) {
    return values.exists(message -> message.getId().equals(messageId));
  }

  public long size() {
    return values.size();
  }

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  public boolean isEmpty() {
    return values.isEmpty();
  }

  public boolean nonEmpty() {
    return values.nonEmpty();
  }

  public Vector<Message> toVector() {
    return values;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Messages messages = (Messages) o;
    return Objects.equals(values, messages.values);
  }

  @Override
  public int hashCode() {
    return Objects.hash(values);
  }

  @Override
  public String toString() {
    return "Messages{" + "values=" + values + '}';
  }

  public static Messages ofEmpty() {
    return new Messages(Vector.empty());
  }

  public static Messages of(Message... values) {
    return new Messages(Vector.of(values));
  }

  public static Messages from(Vector<Message> values) {
    return new Messages(values);
  }
}
