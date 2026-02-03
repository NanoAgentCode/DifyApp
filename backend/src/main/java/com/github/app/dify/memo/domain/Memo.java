package com.github.app.dify.memo.domain;

import com.github.app.dify.common.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import java.util.Date;

/**
 * 备忘录表（MEMO）
 */
@Entity
@Table(name = "MEMO")
public class Memo extends BaseEntity {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_CANCELLED = "cancelled";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "备忘录编号")
    private Long id;

    @Schema(description = "用户ID")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Schema(description = "提醒内容")
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Schema(description = "提醒时间")
    @Column(name = "remind_at", nullable = false)
    private Date remindAt;

    @Size(max = 20)
    @Schema(description = "状态：pending-待提醒，done-已提醒，cancelled-已取消")
    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Schema(description = "周期提醒间隔（分钟），null 表示一次性")
    @Column(name = "interval_minutes")
    private Integer intervalMinutes;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Date getRemindAt() {
        return remindAt;
    }

    public void setRemindAt(Date remindAt) {
        this.remindAt = remindAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(Integer intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }
}
