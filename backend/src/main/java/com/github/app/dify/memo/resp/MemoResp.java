package com.github.app.dify.memo.resp;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

@Schema(description = "备忘录响应")
public class MemoResp {

    @Schema(description = "ID")
    private Long id;
    @Schema(description = "提醒内容")
    private String content;
    @Schema(description = "提醒时间")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Date remindAt;
    @Schema(description = "状态：pending-待提醒，done-已提醒，cancelled-已取消")
    private String status;
    @Schema(description = "周期提醒间隔（分钟），null 表示一次性")
    private Integer intervalMinutes;
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Date createTime;
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
