package com.github.j5ik2o.cqrs.es.java.rmu.dao;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GroupChatMapper {
  @Insert(
      "INSERT INTO group_chats (id, disabled, name, owner_id, created_at, updated_at) VALUES(#{id}, 0, #{name}, #{ownerId}, #{createdAt}, #{updatedAt})")
  @Flush
  int insertGroupChat(GroupChatRecord groupChatRecord);

  @Update("UPDATE group_chats SET name = #{name}, updated_at = #{updatedAt} WHERE id = #{id}")
  @Flush
  int updateGroupChatName(String id, String name);

  @Update("UPDATE group_chats SET disabled = 1 WHERE id = #{id}")
  @Flush
  int deleteGroupChat(String id);
}
