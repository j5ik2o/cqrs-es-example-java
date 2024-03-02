package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.github.f4b6a3.ulid.Ulid;
import java.io.IOException;

public class UlidDeserializer extends JsonDeserializer<Ulid> {
  @Override
  public Ulid deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException, JacksonException {
    return Ulid.from(jsonParser.getValueAsString());
  }
}
