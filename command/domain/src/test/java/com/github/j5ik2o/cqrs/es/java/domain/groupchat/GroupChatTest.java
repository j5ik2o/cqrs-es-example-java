package com.github.j5ik2o.cqrs.es.java.domain.groupchat;

import static org.junit.jupiter.api.Assertions.*;

import com.github.j5ik2o.cqrs.es.java.domain.useraccount.UserAccountId;
import java.time.LocalDateTime;
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

  @Test
  public void testEditMessage() {
    var id = GroupChatId.generate();
    var name = GroupChatName.of("test");
    var executorId = UserAccountId.generate();
    var groupChat = GroupChat.create(id, name, executorId)._1;
    var message = Message.of(MessageId.generate(), "before", executorId, LocalDateTime.now());
    var posted = groupChat.postMessage(message, executorId).get();
    var memberId = MemberId.generate();
    var otherMember = Member.of(memberId, UserAccountId.generate(), MemberRole.MEMBER);
    var withMember = posted._1.addMember(otherMember, executorId).get();
    var edited = withMember._1.editMessage(message.getId(), "after", executorId).get();

    assertEquals(4L, edited._1.getSequenceNumber());
    var event = (GroupChatEvent.MessageEdited) edited._2;
    assertEquals(id, event.aggregateId());
    assertEquals(executorId, event.executorId());
    assertEquals("after", event.message().getContent());
    assertEquals(
        "after", edited._1.getMessages().findByMessageId(message.getId()).get().getContent());
  }

  @Test
  public void testEditMessageRejectsNonSender() {
    var id = GroupChatId.generate();
    var name = GroupChatName.of("test");
    var executorId = UserAccountId.generate();
    var groupChat = GroupChat.create(id, name, executorId)._1;
    var message = Message.of(MessageId.generate(), "before", executorId, LocalDateTime.now());
    var posted = groupChat.postMessage(message, executorId).get();
    var otherAccountId = UserAccountId.generate();
    var otherMember = Member.of(MemberId.generate(), otherAccountId, MemberRole.MEMBER);
    var withMember = posted._1.addMember(otherMember, executorId).get();

    var result = withMember._1.editMessage(message.getId(), "after", otherAccountId);

    assertTrue(result.isLeft());
    assertInstanceOf(GroupChatError.NotSenderError.class, result.getLeft());
  }
}
