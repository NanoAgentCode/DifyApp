package com.github.app.dify.controller;

import com.github.app.dify.req.DrawIOGenerateRequest;
import com.github.app.dify.req.DrawIOModifyRequest;
import com.github.app.dify.req.DrawIOSaveRequest;
import com.github.app.dify.resp.DrawIOGenerateResponse;
import com.github.app.dify.resp.DrawIODiagramResp;
import com.github.app.dify.service.DrawIOService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DrawIO 控制器
 */
@Tag(name = "AI绘图")
@RestController
@RequestMapping("/api/drawio")
public class DrawIOController {
    
    private static final Logger logger = LoggerFactory.getLogger(DrawIOController.class);
    
    @Autowired
    private DrawIOService drawIOService;
    
    /**
     * 生成图表
     */
    @Operation(summary = "生成图表")
    @PostMapping("/generate")
    public ResponseEntity<?> generateDiagram(
            @Validated @RequestBody DrawIOGenerateRequest request,
            HttpServletRequest httpRequest) {
        try {
            logger.info("接收到生成图表请求 - 提示: {}, 类型: {}", request.getPrompt(), request.getDiagramType());
            Long userId = (Long) httpRequest.getAttribute("userId");
            
            if (userId == null) {
                logger.warn("用户ID为空，无法生成图表");
                Map<String, Object> error = new HashMap<>();
                error.put("error", "用户未登录或会话已过期");
                error.put("code", 401);
                return ResponseEntity.status(401).body(error);
            }
            
            DrawIOGenerateResponse response = drawIOService.generateDiagram(request, userId);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            logger.error("生成图表失败 - 状态异常", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("code", 400);
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("生成图表失败 - 系统异常", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "生成图表失败: " + e.getMessage());
            error.put("code", 500);
            error.put("detail", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    /**
     * 修改图表
     */
    @Operation(summary = "修改图表")
    @PostMapping("/modify")
    public ResponseEntity<DrawIOGenerateResponse> modifyDiagram(
            @Validated @RequestBody DrawIOModifyRequest request,
            HttpServletRequest httpRequest) {
        try {
            logger.info("接收到修改图表请求 - 修改指令: {}", request.getPrompt());
            Long userId = (Long) httpRequest.getAttribute("userId");
            
            if (userId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            DrawIOGenerateResponse response = drawIOService.modifyDiagram(request, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("修改图表失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 保存图表
     */
    @Operation(summary = "保存图表")
    @PostMapping("/save")
    public ResponseEntity<DrawIODiagramResp> saveDiagram(
            @Validated @RequestBody DrawIOSaveRequest request,
            HttpServletRequest httpRequest) {
        try {
            logger.info("接收到保存图表请求 - 名称: {}", request.getName());
            Long userId = (Long) httpRequest.getAttribute("userId");
            
            if (userId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            DrawIODiagramResp response = drawIOService.saveDiagram(request, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("保存图表失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取图表列表
     */
    @Operation(summary = "获取图表列表")
    @GetMapping("/list")
    public ResponseEntity<List<DrawIODiagramResp>> getDiagramList(
            HttpServletRequest httpRequest) {
        try {
            Long userId = (Long) httpRequest.getAttribute("userId");
            
            if (userId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            List<DrawIODiagramResp> response = drawIOService.getDiagramList(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取图表列表失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取图表详情
     */
    @Operation(summary = "获取图表详情")
    @GetMapping("/{id}")
    public ResponseEntity<DrawIODiagramResp> getDiagramDetail(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        try {
            Long userId = (Long) httpRequest.getAttribute("userId");
            
            if (userId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            DrawIODiagramResp response = drawIOService.getDiagramDetail(id, userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取图表详情失败 - ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 删除图表
     */
    @Operation(summary = "删除图表")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDiagram(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        try {
            Long userId = (Long) httpRequest.getAttribute("userId");
            
            if (userId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            drawIOService.deleteDiagram(id, userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("删除图表失败 - ID: {}", id, e);
            return ResponseEntity.badRequest().build();
        }
    }
}

