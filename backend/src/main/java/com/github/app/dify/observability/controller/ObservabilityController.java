package com.github.app.dify.observability.controller;

import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.common.resp.ApiResponse;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.common.util.PageUtil;
import com.github.app.dify.observability.domain.LLMTrace;
import com.github.app.dify.observability.service.LLMTraceService;
import com.github.app.dify.observability.service.impl.LLMTraceServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * LLM 可观测性管理
 * 
 * 简化设计：
 * 1. 所有操作基于 ES 文档 ID（UUID 格式）
 * 2. 列表返回的 esDocId 就是查询/删除时使用的 ID
 */
@Tag(name = "Observability管理")
@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    private static final Logger logger = LoggerFactory.getLogger(ObservabilityController.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private LLMTraceService traceService;

    /**
     * 查询追踪列表
     */
    @Operation(summary = "查询追踪列表")
    @GetMapping("/traces")
    public ApiResponse<PageResponse<LLMTrace>> listTraces(
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String traceSource,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageUtil.createPageable(page, size, "createdAt", false);

        LocalDateTime start = parseTime(startTime);
        LocalDateTime end = parseTime(endTime);

        Page<LLMTrace> pageResult = traceService.listTraces(
                model, provider, traceSource, conversationId, start, end, pageable);

        return ApiResponse.success(PageUtil.toPageResponse(pageResult, x -> x));
    }

    /**
     * 查询追踪详情
     */
    @Operation(summary = "查询追踪详情")
    @GetMapping("/traces/{id}")
    public ApiResponse<LLMTrace> getTrace(@PathVariable("id") String id) {
        if (isInvalidId(id)) {
            return ApiResponse.error("无效的ID");
        }

        try {
            if (traceService instanceof LLMTraceServiceImpl impl) {
                LLMTrace trace = impl.getByDocId(id);
                return ApiResponse.success(trace);
            }
            return ApiResponse.error("服务不可用");
        } catch (NotFoundException e) {
            return ApiResponse.error("追踪不存在");
        } catch (Exception e) {
            logger.error("查询失败: id={}", id, e);
            return ApiResponse.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 删除追踪
     */
    @Operation(summary = "删除追踪")
    @DeleteMapping("/traces/{id}")
    public ApiResponse<Void> deleteTrace(@PathVariable("id") String id) {
        if (isInvalidId(id)) {
            return ApiResponse.error("无效的ID");
        }

        try {
            if (traceService instanceof LLMTraceServiceImpl impl) {
                impl.deleteByDocId(id);
                return ApiResponse.success(null);
            }
            return ApiResponse.error("服务不可用");
        } catch (NotFoundException e) {
            return ApiResponse.error("追踪不存在");
        } catch (Exception e) {
            logger.error("删除失败: id={}", id, e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取模型选项
     */
    @Operation(summary = "获取模型选项")
    @GetMapping("/models")
    public ApiResponse<List<String>> getModels() {
        try {
            if (traceService instanceof LLMTraceServiceImpl impl) {
                return ApiResponse.success(impl.getModels());
            }
            return ApiResponse.success(new ArrayList<>());
        } catch (Exception e) {
            logger.error("获取模型选项失败", e);
            return ApiResponse.error("获取失败");
        }
    }

    /**
     * 获取供应商选项
     */
    @Operation(summary = "获取供应商选项")
    @GetMapping("/providers")
    public ApiResponse<List<String>> getProviders() {
        try {
            if (traceService instanceof LLMTraceServiceImpl impl) {
                return ApiResponse.success(impl.getProviders());
            }
            return ApiResponse.success(new ArrayList<>());
        } catch (Exception e) {
            logger.error("获取供应商选项失败", e);
            return ApiResponse.error("获取失败");
        }
    }

    /**
     * 获取追踪来源选项
     */
    @Operation(summary = "获取追踪来源选项")
    @GetMapping("/trace-sources")
    public ApiResponse<List<String>> getTraceSources() {
        try {
            if (traceService instanceof LLMTraceServiceImpl impl) {
                return ApiResponse.success(impl.getTraceSources());
            }
            return ApiResponse.success(new ArrayList<>());
        } catch (Exception e) {
            logger.error("获取追踪来源选项失败", e);
            return ApiResponse.error("获取失败");
        }
    }

    // ==================== 辅助方法 ====================

    private boolean isInvalidId(String id) {
        return id == null || id.isEmpty() || "null".equalsIgnoreCase(id);
    }

    private LocalDateTime parseTime(String time) {
        if (time == null || time.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(time, FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }
}
