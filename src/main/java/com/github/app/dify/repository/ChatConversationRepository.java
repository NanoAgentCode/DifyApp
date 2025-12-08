package com.github.app.dify.repository;

import com.github.app.dify.domain.ChatConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;
/**
 * 对话会话Repository
 */
@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long>, JpaSpecificationExecutor<ChatConversation> {

    /**
     * 根据用户ID查找会话列表（未删除）
     */
    @Query("SELECT c FROM ChatConversation c WHERE c.userId = :userId " +
           "AND (c.deleted IS NULL OR c.deleted = 0) ORDER BY c.createTime DESC")
    Page<ChatConversation> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * 根据用户ID和会话类型查找会话列表（未删除）
     */
    @Query("SELECT c FROM ChatConversation c WHERE c.userId = :userId AND c.type = :type " +
           "AND (c.deleted IS NULL OR c.deleted = 0) ORDER BY c.createTime DESC")
    Page<ChatConversation> findByUserIdAndType(@Param("userId") Long userId, 
                                                @Param("type") Integer type, 
                                                Pageable pageable);

    /**
     * 根据用户ID和关键词搜索会话（按标题）
     */
    @Query("SELECT c FROM ChatConversation c WHERE c.userId = :userId " +
           "AND (c.deleted IS NULL OR c.deleted = 0) " +
           "AND (c.title LIKE CONCAT('%', :keyword, '%')) ORDER BY c.createTime DESC")
    Page<ChatConversation> searchByUserIdAndKeyword(@Param("userId") Long userId, 
                                                      @Param("keyword") String keyword, 
                                                      Pageable pageable);

    /**
     * 根据ID和用户ID查找会话（确保用户只能访问自己的会话）
     */
    @Query("SELECT c FROM ChatConversation c WHERE c.id = :id AND c.userId = :userId " +
           "AND (c.deleted IS NULL OR c.deleted = 0)")
    Optional<ChatConversation> findByIdAndUserId(@Param("id") Long id, 
                                                   @Param("userId") Long userId);

    /**
     * 管理员：根据条件查询所有会话（支持筛选）
     * 注意：此方法在Service层会被动态调用，根据参数是否为NULL选择不同的查询
     */
    @Query("SELECT c FROM ChatConversation c WHERE " +
           "(c.deleted IS NULL OR c.deleted = 0) " +
           "ORDER BY c.createTime DESC")
    Page<ChatConversation> findAllNotDeleted(Pageable pageable);

    /**
     * 统计用户的会话数量（未删除）
     */
    @Query("SELECT COUNT(c) FROM ChatConversation c WHERE c.userId = :userId " +
           "AND (c.deleted IS NULL OR c.deleted = 0)")
    Long countByUserId(@Param("userId") Long userId);

    /**
     * 统计所有会话数量（未删除）
     */
    @Query("SELECT COUNT(c) FROM ChatConversation c WHERE (c.deleted IS NULL OR c.deleted = 0)")
    Long countAll();

    /**
     * 根据用户ID查找所有会话（未删除，不分页）
     */
    @Query("SELECT c FROM ChatConversation c WHERE c.userId = :userId " +
           "AND (c.deleted IS NULL OR c.deleted = 0) ORDER BY c.createTime DESC")
    List<ChatConversation> findAllByUserId(@Param("userId") Long userId);
}