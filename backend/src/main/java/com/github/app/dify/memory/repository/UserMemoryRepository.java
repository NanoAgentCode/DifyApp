package com.github.app.dify.memory.repository;

import com.github.app.dify.memory.domain.UserMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId " +
            "AND (m.deleted IS NULL OR m.deleted = 0) ORDER BY m.updateTime DESC")
    List<UserMemory> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId AND m.memoryType = :memoryType " +
            "AND (m.deleted IS NULL OR m.deleted = 0) ORDER BY m.updateTime DESC")
    List<UserMemory> findRecentByUserIdAndType(@Param("userId") Long userId,
                                               @Param("memoryType") String memoryType,
                                               Pageable pageable);

    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId AND m.memoryType = :memoryType AND m.memoryKey = :memoryKey " +
            "AND (m.deleted IS NULL OR m.deleted = 0)")
    Optional<UserMemory> findActiveByUserIdAndTypeAndKey(@Param("userId") Long userId,
                                                         @Param("memoryType") String memoryType,
                                                         @Param("memoryKey") String memoryKey);

    @Query("SELECT COUNT(m) FROM UserMemory m WHERE m.userId = :userId AND m.memoryType = :memoryType " +
            "AND (m.deleted IS NULL OR m.deleted = 0)")
    long countActiveByUserIdAndType(@Param("userId") Long userId, @Param("memoryType") String memoryType);

    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId AND m.memoryType = :memoryType " +
            "AND (m.deleted IS NULL OR m.deleted = 0) ORDER BY m.updateTime ASC")
    List<UserMemory> findOldestActiveByUserIdAndType(@Param("userId") Long userId,
                                                     @Param("memoryType") String memoryType,
                                                     Pageable pageable);

    @Query("UPDATE UserMemory m SET m.deleted = 1, m.updateTime = CURRENT_TIMESTAMP WHERE m.userId = :userId " +
            "AND (m.deleted IS NULL OR m.deleted = 0)")
    @org.springframework.data.jpa.repository.Modifying
    int softDeleteAllByUserId(@Param("userId") Long userId);
}
