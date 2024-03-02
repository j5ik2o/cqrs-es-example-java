package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.vavr.collection.Vector;
import java.io.IOException;
import java.util.List;

public class MessagesDeserializer extends JsonDeserializer<Messages> {
  @Override
  public Messages deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException, JacksonException {
    List<Message> list = jsonParser.readValueAs(new TypeReference<List<Message>>() {});
    return Messages.from(Vector.ofAll(list));
  }
}
