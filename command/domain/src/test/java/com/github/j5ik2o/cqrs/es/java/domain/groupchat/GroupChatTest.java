package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import static org.junit.jupiter.api.Assertions.*;

import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import org.junit.jupiter.api.Test;

class GroupChatTest {

  @Test
  public void testCreateGroupChat() {
    var id = GroupChatId.generate();
    var name = GroupChatName.of("test");
    var executorId = UserAccountId.generate();
    var result = GroupChat.create(id, name, executorId);
    var groupChat = result._1;
    var groupChatCreated = (GroupChatEvent.Created) result._2;

    assertEquals(id, groupChat.getId());
    assertEquals(name, groupChat.getName());

    assertEquals(id, groupChatCreated.aggregateId());
    assertEquals(name, groupChatCreated.name());
    assertEquals(executorId, groupChatCreated.executorId());
  }

  @Test
  public void testDeleteGroupChat() {
    var id = GroupChatId.generate();
    var name = GroupChatName.of("test");
    var executorId = UserAccountId.generate();
    var result1 = GroupChat.create(id, name, executorId);
    var groupChat = result1._1;

    var resultEither2 = groupChat.delete(executorId);
    var result2 = resultEither2.get();
    var groupChat2 = result2._1;
    var groupChatDeleted = (GroupChatEvent.Deleted) result2._2;

    assertTrue(groupChat2.isDeleted());
    assertEquals(id, groupChatDeleted.aggregateId());
    assertEquals(executorId, groupChatDeleted.executorId());
  }
}
