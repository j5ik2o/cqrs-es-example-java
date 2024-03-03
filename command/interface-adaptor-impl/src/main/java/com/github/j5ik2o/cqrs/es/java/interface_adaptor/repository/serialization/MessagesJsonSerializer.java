package com.github.j5ik2o.cqrs.es.java.interface_adaptor.repository.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.Messages;
import java.io.IOException;

public final class MessagesJsonSerializer extends JsonSerializer<Messages> {
  @Override
  public void serialize(
      Messages messages, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeObject(messages.toVector().toJavaList());
  }
}
