package com.github.app.dify.ops.trace.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务步骤追踪事件。
 */
@Data
public class TraceStep {

    private String stepCode;

    private String stepName;

    /**
     * SUCCESS / FAILED
     */
    private String status;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private Long durationMs;

    private String inputSummary;

    private String outputSummary;

    private String errorSummary;
}

