package com.github.app.dify.memory.resp;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Date;

@Schema(description = "用户记忆条目")
public class UserMemoryItemResp {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "记忆类型：long_term/entity")
    private String memoryType;

    @Schema(description = "记忆键")
    private String memoryKey;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "重要度（0-5）")
    private Integer importance;

    @Schema(description = "更新时间")
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getMemoryKey() {
        return memoryKey;
    }

    public void setMemoryKey(String memoryKey) {
        this.memoryKey = memoryKey;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getImportance() {
        return importance;
    }

    public void setImportance(Integer importance) {
        this.importance = importance;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}

