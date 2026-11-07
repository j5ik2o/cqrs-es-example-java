package com.github.j5ik2o.cqrs.es.java.rmu.dao;

import java.time.Instant;
import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MessageWriteMapper {
  @Insert(
      "INSERT INTO messages (id, disabled, group_chat_id, user_account_id, text, created_at, updated_at) VALUES(#{id}, 0, #{groupChatId}, #{userAccountId}, #{text}, #{createdAt}, #{updatedAt})")
  @Flush
  int insertMessage(MessageRecord messageRecord);

  @Update("UPDATE messages SET text = #{text}, updated_at = #{updatedAt} WHERE id = #{id}")
  @Flush
  int updateMessage(MessageRecord messageRecord);

  @Update("UPDATE messages SET disabled = 1, updated_at = #{updatedAt} WHERE id = #{id}")
  @Flush
  int deleteMessage(@Param("id") String id, @Param("updatedAt") Instant updatedAt);
}
