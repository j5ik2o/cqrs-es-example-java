package com.github.j5ik2o.cqrs.es.java.command.interface_adaptor.repository.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.f4b6a3.ulid.Ulid;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChat;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.GroupChatId;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.Members;
import com.github.j5ik2o.cqrs.es.java.domain.groupchat.Messages;
import com.github.j5ik2o.event.store.adapter.java.DeserializationException;
import com.github.j5ik2o.event.store.adapter.java.SerializationException;
import com.github.j5ik2o.event.store.adapter.java.SnapshotSerializer;
import java.io.IOException;
import javax.annotation.Nonnull;

public final class GroupChatSnapshotSerializer
    implements SnapshotSerializer<GroupChatId, GroupChat> {
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static void configureModule() {
    SimpleModule module = new SimpleModule();
    module.addSerializer(Ulid.class, new UlidJsonSerializer());
    module.addDeserializer(Ulid.class, new UlidJsonDeserializer());
    module.addSerializer(Members.class, new MembersJsonSerializer());
    module.addDeserializer(Members.class, new MembersJsonDeserializer());
    module.addSerializer(Messages.class, new MessagesJsonSerializer());
    module.addDeserializer(Messages.class, new MessagesJsonDeserializer());
    objectMapper.registerModule(module);
    objectMapper.findAndRegisterModules();
  }

  static {
    configureModule();
  }

  @Nonnull
  @Override
  public byte[] serialize(@Nonnull GroupChat groupChat) throws SerializationException {
    try {
      return objectMapper.writeValueAsBytes(groupChat);
    } catch (JsonProcessingException e) {
      throw new SerializationException(e);
    }
  }

  @Nonnull
  @Override
  public GroupChat deserialize(@Nonnull byte[] bytes, @Nonnull Class<GroupChat> clazz)
      throws DeserializationException {
    try {
      return objectMapper.readValue(bytes, clazz);
    } catch (IOException e) {
      throw new DeserializationException(e);
    }
  }
}