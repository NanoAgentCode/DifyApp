package com.github.app.dify.observability.controller;

import com.github.app.dify.common.resp.ApiResponse;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.common.util.PageUtil;
import com.github.app.dify.observability.domain.LLMTrace;
import com.github.app.dify.observability.service.LLMTraceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    @Autowired
    private LLMTraceService traceService;

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

        // Use "createdAt" for sorting
        Pageable pageable = PageUtil.createPageable(page, size, "createdAt", false);

        LocalDateTime start = null;
        LocalDateTime end = null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        if (startTime != null && !startTime.isEmpty()) {
            start = LocalDateTime.parse(startTime, formatter);
        }
        if (endTime != null && !endTime.isEmpty()) {
            end = LocalDateTime.parse(endTime, formatter);
        }

        Page<LLMTrace> pageResult = traceService.listTraces(model, provider, traceSource, conversationId, start, end,
                pageable);

        return ApiResponse.success(PageUtil.toPageResponse(pageResult, x -> x));
    }

    @GetMapping("/traces/{id}")
    public ApiResponse<LLMTrace> getTrace(@PathVariable("id") Long id) {
        return ApiResponse.success(traceService.getTrace(id));
    }

    @DeleteMapping("/traces/{id}")
    public ApiResponse<Void> deleteTrace(@PathVariable("id") Long id) {
        traceService.deleteTrace(id);
        return ApiResponse.success(null);
    }
}
