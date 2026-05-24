package com.github.app.dify.memo.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Date;

@Schema(description = "Confirmed memo creation request")
public class MemoConfirmReq {

    @NotBlank(message = "Content cannot be empty")
    @Schema(description = "Reminder content", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @NotNull(message = "Reminder time cannot be empty")
    @Future(message = "Reminder time must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    @Schema(description = "Reminder time", requiredMode = Schema.RequiredMode.REQUIRED)
    private Date remindAt;

    @Schema(description = "Repeat interval in minutes")
    private Integer intervalMinutes;

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

    public Integer getIntervalMinutes() {
        return intervalMinutes;
    }

    public void setIntervalMinutes(Integer intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }
}
