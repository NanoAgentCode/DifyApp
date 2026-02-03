package com.github.app.dify.memo.repository;

import com.github.app.dify.memo.domain.Memo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface MemoRepository extends JpaRepository<Memo, Long> {

    @Query("SELECT m FROM Memo m WHERE m.userId = :userId AND (m.deleted IS NULL OR m.deleted = 0) " +
            "ORDER BY m.remindAt ASC")
    List<Memo> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT m FROM Memo m WHERE m.userId = :userId AND (m.deleted IS NULL OR m.deleted = 0) " +
            "AND (:status IS NULL OR :status = '' OR m.status = :status) " +
            "ORDER BY m.remindAt ASC")
    List<Memo> findAllByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status, Pageable pageable);

    @Query("SELECT m FROM Memo m WHERE m.id = :id AND m.userId = :userId AND (m.deleted IS NULL OR m.deleted = 0)")
    Optional<Memo> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT m FROM Memo m WHERE m.userId = :userId AND (m.deleted IS NULL OR m.deleted = 0) " +
            "AND m.status = 'pending' AND m.remindAt <= :now ORDER BY m.remindAt ASC")
    List<Memo> findDueByUserId(@Param("userId") Long userId, @Param("now") Date now);

    @Query("SELECT m FROM Memo m WHERE (m.deleted IS NULL OR m.deleted = 0) " +
            "AND m.status = 'pending' AND m.remindAt <= :now ORDER BY m.remindAt ASC")
    List<Memo> findAllDue(@Param("now") Date now);

    /** 查询已到期且超过宽限时间的待提醒项（供定时任务自动标记，避免与前端弹窗争抢） */
    @Query("SELECT m FROM Memo m WHERE (m.deleted IS NULL OR m.deleted = 0) " +
            "AND m.status = 'pending' AND m.remindAt <= :before ORDER BY m.remindAt ASC")
    List<Memo> findAllDueBefore(@Param("before") Date before);

    @Modifying
    @Query("UPDATE Memo m SET m.status = :status, m.updateTime = CURRENT_TIMESTAMP WHERE m.id = :id AND m.userId = :userId AND (m.deleted IS NULL OR m.deleted = 0)")
    int updateStatusByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId, @Param("status") String status);

    @Modifying
    @Query("UPDATE Memo m SET m.deleted = 1, m.updateTime = CURRENT_TIMESTAMP WHERE m.id = :id AND m.userId = :userId AND (m.deleted IS NULL OR m.deleted = 0)")
    int softDeleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** 定时任务用：将已到期的待提醒项标记为已提醒 */
    @Modifying
    @Query("UPDATE Memo m SET m.status = 'done', m.updateTime = CURRENT_TIMESTAMP WHERE m.id = :id AND m.status = 'pending' AND (m.deleted IS NULL OR m.deleted = 0)")
    int markDoneById(@Param("id") Long id);

    /** 周期提醒：将下次提醒时间推进 interval_minutes，保持 pending */
    @Modifying
    @Query("UPDATE Memo m SET m.remindAt = :nextRemindAt, m.updateTime = CURRENT_TIMESTAMP WHERE m.id = :id AND m.status = 'pending' AND (m.deleted IS NULL OR m.deleted = 0)")
    int updateRemindAtById(@Param("id") Long id, @Param("nextRemindAt") java.util.Date nextRemindAt);
}
