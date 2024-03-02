package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

public final class MembersJsonSerializer extends JsonSerializer<Members> {
  @Override
  public void serialize(
      Members members, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeObject(members.toVector().toJavaList());
  }
}
