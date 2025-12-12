package com.github.app.dify.chat.repository;

import com.github.app.dify.chat.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
/**
 * 对话消息Repository
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 根据会话ID查找消息列表（按顺序排序）
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.conversationId = :conversationId " +
           "ORDER BY m.sequence ASC")
    List<ChatMessage> findByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 根据会话ID查找消息数量
     */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.conversationId = :conversationId")
    Long countByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 根据会话ID统计对话轮数（用户消息数，一问一答为一轮）
     */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.conversationId = :conversationId AND m.role = 'user'")
    Long countConversationRoundsByConversationId(@Param("conversationId") Long conversationId);

    /**
     * 获取会话中最大的sequence值
     */
    @Query("SELECT COALESCE(MAX(m.sequence), 0) FROM ChatMessage m WHERE m.conversationId = :conversationId")
    Integer getMaxSequenceByConversationId(@Param("conversationId") Long conversationId);
}