package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.Member;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.Members;
import io.vavr.collection.Vector;
import java.io.IOException;
import java.util.List;

public final class MembersJsonDeserializer extends JsonDeserializer<Members> {
  @Override
  public Members deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException, JacksonException {
    List<Member> list = jsonParser.readValueAs(new TypeReference<List<Member>>() {});
    return Members.from(Vector.ofAll(list));
  }
}
