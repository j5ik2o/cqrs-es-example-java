package com.github.j5ik2o.cqrs.es.java.query.interface_adaptor.dao;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MessageMapper {
  @Select(
      """
            SELECT m.id, m.group_chat_id, m.user_account_id, m.text, m.created_at, m.updated_at
            FROM group_chats AS gc JOIN messages AS m ON gc.id = m.group_chat_id
            WHERE gc.disabled = 'false' AND m.disabled = 'false' AND m.id = #{messageId}
            AND EXISTS ( SELECT 1 FROM members AS mem WHERE mem.group_chat_id = m.group_chat_id AND mem.user_account_id = #{userAccountId}
            """)
  Optional<MessageRecord> getMessage(String messageId, String userAccountId);

  @Select(
      """
            SELECT m.id, m.group_chat_id, m.user_account_id, m.text, m.created_at, m.updated_at
            FROM group_chats AS gc JOIN messages AS m ON gc.id = m.group_chat_id
            WHERE gc.disabled = 'false' AND m.disabled = 'false' AND m.group_chat_id = #{groupChatId}
            AND EXISTS (SELECT 1 FROM members AS mem WHERE mem.group_chat_id = m.group_chat_id AND mem.user_account_id = #{userAccountId}
            """)
  List<MessageRecord> getMessages(String groupChatId, String userAccountId);
}
