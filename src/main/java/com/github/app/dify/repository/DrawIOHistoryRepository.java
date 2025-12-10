package com.github.app.dify.repository;

import com.github.app.dify.domain.DrawIOHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * DrawIO 历史记录 Repository
 */
@Repository
public interface DrawIOHistoryRepository extends JpaRepository<DrawIOHistory, Long> {
    
    /**
     * 根据用户ID查询未删除的历史记录列表（按创建时间倒序，最多返回10条）
     */
    @Query("SELECT h FROM DrawIOHistory h WHERE h.userId = :userId AND (h.deleted IS NULL OR h.deleted = 0) ORDER BY h.createTime DESC")
    List<DrawIOHistory> findByUserIdAndNotDeleted(@Param("userId") Long userId);
    
    /**
     * 根据用户ID查询未删除的历史记录列表（限制数量）
     */
    @Query(value = "SELECT * FROM DRAWIO_HISTORY WHERE user_id = :userId AND (deleted IS NULL OR deleted = 0) ORDER BY create_time DESC LIMIT 10", nativeQuery = true)
    List<DrawIOHistory> findByUserIdAndNotDeletedLimit(@Param("userId") Long userId);
    
    /**
     * 根据ID和用户ID查询历史记录
     */
    @Query("SELECT h FROM DrawIOHistory h WHERE h.id = :id AND h.userId = :userId AND (h.deleted IS NULL OR h.deleted = 0)")
    java.util.Optional<DrawIOHistory> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}

