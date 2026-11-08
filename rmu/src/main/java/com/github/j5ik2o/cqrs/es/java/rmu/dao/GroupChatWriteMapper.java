package com.github.j5ik2o.cqrs.es.java.rmu.dao;

import java.time.Instant;
import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GroupChatWriteMapper {
  @Insert(
      "INSERT INTO group_chats (id, disabled, name, owner_id, created_at, updated_at) VALUES(#{id}, 0, #{name}, #{ownerId}, #{createdAt}, #{updatedAt})")
  @Flush
  int insertGroupChat(GroupChatRecord groupChatRecord);

  @Update("UPDATE group_chats SET name = #{name}, updated_at = #{updatedAt} WHERE id = #{id}")
  @Flush
  int updateGroupChatName(
      @Param("id") String id, @Param("name") String name, @Param("updatedAt") Instant updatedAt);

  @Update("UPDATE group_chats SET disabled = 1, updated_at = #{updatedAt} WHERE id = #{id}")
  @Flush
  int deleteGroupChat(@Param("id") String id, @Param("updatedAt") Instant updatedAt);
}
