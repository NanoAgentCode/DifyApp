package com.github.app.dify.statistics.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.ForbiddenException;
import com.github.app.dify.statistics.resp.StatisticsResponse;
import com.github.app.dify.statistics.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 统计控制器
 */
@Tag(name = "数据统计")
@RestController
@RequestMapping("/api/admin/statistics")
public class StatisticsController extends BaseController {
    
    @Autowired
    private StatisticsService statisticsService;
    
    /**
     * 检查是否为管理员
     */
    private void checkAdmin(HttpServletRequest request) {
        Object roleObj = request.getAttribute("role");
        if (!(roleObj instanceof Integer) || (Integer) roleObj != 1) {
            throw new ForbiddenException("需要管理员权限");
        }
    }
    
    /**
     * 获取所有统计数据
     */
    @Operation(summary = "获取所有统计数据")
    @GetMapping
    public ResponseEntity<StatisticsResponse> getAllStatistics(
            @RequestParam(required = false) Integer days,
            HttpServletRequest request) {
        checkAdmin(request);
        StatisticsResponse response = statisticsService.getAllStatistics(days);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取概览统计
     */
    @Operation(summary = "获取概览统计")
    @GetMapping("/overview")
    public ResponseEntity<StatisticsResponse.OverviewStatistics> getOverviewStatistics(HttpServletRequest request) {
        checkAdmin(request);
        StatisticsResponse.OverviewStatistics response = statisticsService.getOverviewStatistics();
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取用户统计
     */
    @Operation(summary = "获取用户统计")
    @GetMapping("/users")
    public ResponseEntity<StatisticsResponse.UserStatistics> getUserStatistics(HttpServletRequest request) {
        checkAdmin(request);
        StatisticsResponse.UserStatistics response = statisticsService.getUserStatistics();
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取应用统计
     */
    @Operation(summary = "获取应用统计")
    @GetMapping("/apps")
    public ResponseEntity<StatisticsResponse.AppStatistics> getAppStatistics(HttpServletRequest request) {
        checkAdmin(request);
        StatisticsResponse.AppStatistics response = statisticsService.getAppStatistics();
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取知识库统计
     */
    @Operation(summary = "获取知识库统计")
    @GetMapping("/knowledge-bases")
    public ResponseEntity<StatisticsResponse.KnowledgeBaseStatistics> getKnowledgeBaseStatistics(HttpServletRequest request) {
        checkAdmin(request);
        StatisticsResponse.KnowledgeBaseStatistics response = statisticsService.getKnowledgeBaseStatistics();
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取模型Token统计
     */
    @Operation(summary = "获取模型Token统计")
    @GetMapping("/model-tokens")
    public ResponseEntity<StatisticsResponse.ModelTokenStatistics> getModelTokenStatistics(HttpServletRequest request) {
        checkAdmin(request);
        StatisticsResponse.ModelTokenStatistics response = statisticsService.getModelTokenStatistics();
        return ResponseEntity.ok(response);
    }
}

