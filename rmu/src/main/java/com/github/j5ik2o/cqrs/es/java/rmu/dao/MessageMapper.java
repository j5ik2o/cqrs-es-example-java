package com.github.j5ik2o.cqrs.es.java.rmu.dao;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MessageMapper {
  @Insert(
      "INSERT INTO messages (id, disabled, group_chat_id, content, sender_id, created_at, updated_at) VALUES(#{id}, 0, #{groupChatId}, #{content}, #{senderId}, #{createdAt}, #{updatedAt})")
  @Flush
  int insertMessage(MessageRecord messageRecord);

  @Update(
      "UPDATE messages SET group_chat_id = #{groupChatId}, content = #{content}, sender_id = #{senderId}, updated_at = #{updatedAt} WHERE id = #{id}")
  @Flush
  int updateMessage(MessageRecord messageRecord);

  @Update("UPDATE messages SET disabled = 1 WHERE id = #{id}")
  @Flush
  int deleteMessage(String id);
}
