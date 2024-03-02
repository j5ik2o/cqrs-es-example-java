package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.github.f4b6a3.ulid.Ulid;
import java.io.IOException;

public final class UlidJsonSerializer extends JsonSerializer<Ulid> {
  @Override
  public void serialize(
      Ulid ulid, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    jsonGenerator.writeString(ulid.toString());
  }
}
