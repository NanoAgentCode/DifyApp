package com.github.app.dify.repository;

import com.github.app.dify.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
/**
 * 用户Repository
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * 根据用户名查找用户
     */
    Optional<User> findByUsername(String username);
    
    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);
    
    /**
     * 根据关键词搜索用户（用户名）
     */
    @Query("SELECT u FROM User u WHERE (u.deleted IS NULL OR u.deleted = 0) " +
           "AND LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<User> searchByKeyword(@Param("keyword") String keyword);
    
    /**
     * 根据关键词、状态和角色搜索用户
     */
    @Query("SELECT u FROM User u WHERE (u.deleted IS NULL OR u.deleted = 0) " +
           "AND (:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR u.status = :status) " +
           "AND (:role IS NULL OR u.role = :role)")
    List<User> searchByFilters(@Param("keyword") String keyword, 
                                @Param("status") Integer status, 
                                @Param("role") Integer role);
    
    /**
     * 根据关键词、状态和角色搜索用户（分页）
     */
    @Query("SELECT u FROM User u WHERE (u.deleted IS NULL OR u.deleted = 0) " +
           "AND (:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:status IS NULL OR u.status = :status) " +
           "AND (:role IS NULL OR u.role = :role)")
    Page<User> searchByFiltersWithPagination(@Param("keyword") String keyword, 
                                             @Param("status") Integer status, 
                                             @Param("role") Integer role,
                                             Pageable pageable);
}