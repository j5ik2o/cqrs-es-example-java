package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.f4b6a3.ulid.Ulid;
import com.github.j5ik2o.event.store.adapter.java.DeserializationException;
import com.github.j5ik2o.event.store.adapter.java.EventSerializer;
import com.github.j5ik2o.event.store.adapter.java.SerializationException;
import java.io.IOException;
import javax.annotation.Nonnull;

public class GroupChatEventSerializer implements EventSerializer<GroupChatId, GroupChatEvent> {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static void configureModule() {
    SimpleModule module = new SimpleModule();
    module.addSerializer(Ulid.class, new UlidSerializer());
    module.addDeserializer(Ulid.class, new UlidDeserializer());
    module.addSerializer(Members.class, new MembersSerializer());
    module.addDeserializer(Members.class, new MembersDeserializer());
    module.addSerializer(Messages.class, new MessagesSerializer());
    module.addDeserializer(Messages.class, new MessagesDeserializer());
    objectMapper.registerModule(module);
    objectMapper.findAndRegisterModules();
  }

  static {
    configureModule();
  }

  @Nonnull
  @Override
  public byte[] serialize(@Nonnull GroupChatEvent groupChatEvent) throws SerializationException {
    try {
      return objectMapper.writeValueAsBytes(groupChatEvent);
    } catch (JsonProcessingException e) {
      throw new SerializationException(e);
    }
  }

  @Nonnull
  @Override
  public GroupChatEvent deserialize(@Nonnull byte[] bytes, @Nonnull Class<GroupChatEvent> clazz)
      throws DeserializationException {
    try {
      return objectMapper.readValue(bytes, clazz);
    } catch (IOException e) {
      throw new DeserializationException(e);
    }
  }
}
