package com.github.j5ik2o.cqrs.es.java.interface_adaptor.repository.serialization;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.Members;
import java.io.IOException;

public final class MembersJsonSerializer extends JsonSerializer<Members> {
  @Override
  public void serialize(
      Members members, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeObject(members.toVector().toJavaList());
  }
}
