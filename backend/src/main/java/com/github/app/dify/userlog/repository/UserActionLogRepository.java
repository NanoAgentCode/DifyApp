package com.github.app.dify.userlog.repository;

import com.github.app.dify.userlog.domain.UserActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户行为日志Repository
 */
@Repository
public interface UserActionLogRepository extends JpaRepository<UserActionLog, Long> {

    /**
     * 根据用户ID查询日志
     */
    List<UserActionLog> findByUserId(Long userId);

    /**
     * 根据用户ID分页查询日志
     */
    Page<UserActionLog> findByUserId(Long userId, Pageable pageable);

    /**
     * 根据模块查询日志
     */
    List<UserActionLog> findByModule(String module);

    /**
     * 根据操作类型查询日志
     */
    List<UserActionLog> findByActionType(String actionType);

    /**
     * 根据结果查询日志
     */
    List<UserActionLog> findByResult(String result);

    /**
     * 根据时间范围查询日志
     */
    @Query("SELECT l FROM UserActionLog l WHERE l.createTime BETWEEN :startTime AND :endTime ORDER BY l.createTime DESC")
    List<UserActionLog> findByTimeRange(@Param("startTime") LocalDateTime startTime, 
                                         @Param("endTime") LocalDateTime endTime);

    /**
     * 根据多条件查询日志（分页）
     */
    @Query("SELECT l FROM UserActionLog l WHERE " +
           "(:userId IS NULL OR l.userId = :userId) AND " +
           "(:username IS NULL OR l.username LIKE CONCAT('%', :username, '%')) AND " +
           "(:module IS NULL OR l.module = :module) AND " +
           "(:actionType IS NULL OR l.actionType = :actionType) AND " +
           "(:result IS NULL OR l.result = :result) AND " +
           "(:startTime IS NULL OR l.createTime >= :startTime) AND " +
           "(:endTime IS NULL OR l.createTime <= :endTime) " +
           "ORDER BY l.createTime DESC")
    Page<UserActionLog> findByConditions(
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("module") String module,
            @Param("actionType") String actionType,
            @Param("result") String result,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * 统计某个时间段内的操作次数
     */
    @Query("SELECT COUNT(l) FROM UserActionLog l WHERE l.createTime BETWEEN :startTime AND :endTime")
    long countByTimeRange(@Param("startTime") LocalDateTime startTime, 
                          @Param("endTime") LocalDateTime endTime);

    /**
     * 统计某个用户的操作次数
     */
    long countByUserId(Long userId);

    /**
     * 统计某个模块的操作次数
     */
    long countByModule(String module);
}
