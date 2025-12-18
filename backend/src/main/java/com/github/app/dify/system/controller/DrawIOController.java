package com.github.app.dify.system.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.system.req.DrawIOGenerateRequest;
import com.github.app.dify.system.req.DrawIOModifyRequest;
import com.github.app.dify.system.req.DrawIOSaveRequest;
import com.github.app.dify.system.req.DrawIOHistoryRequest;
import com.github.app.dify.system.resp.DrawIOGenerateResponse;
import com.github.app.dify.system.resp.DrawIODiagramResp;
import com.github.app.dify.system.resp.DrawIOHistoryResp;
import com.github.app.dify.system.service.DrawIOService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * DrawIO 控制器
 */
@Tag(name = "AI绘图")
@RestController
@RequestMapping("/api/drawio")
public class DrawIOController extends BaseController {
    
    @Autowired
    private DrawIOService drawIOService;
    
    /**
     * 生成图表
     */
    @Operation(summary = "生成图表")
    @PostMapping("/generate")
    public ResponseEntity<DrawIOGenerateResponse> generateDiagram(
            @Validated @RequestBody DrawIOGenerateRequest request,
            HttpServletRequest httpRequest) {
        logger.info("接收到生成图表请求 - 提示: {}, 类型: {}", request.getPrompt(), request.getDiagramType());
        Long userId = getUserId(httpRequest);
        DrawIOGenerateResponse response = drawIOService.generateDiagram(request, userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 修改图表
     */
    @Operation(summary = "修改图表")
    @PostMapping("/modify")
    public ResponseEntity<DrawIOGenerateResponse> modifyDiagram(
            @Validated @RequestBody DrawIOModifyRequest request,
            HttpServletRequest httpRequest) {
        logger.info("接收到修改图表请求 - 修改指令: {}", request.getPrompt());
        Long userId = getUserId(httpRequest);
        DrawIOGenerateResponse response = drawIOService.modifyDiagram(request, userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 保存图表
     */
    @Operation(summary = "保存图表")
    @PostMapping("/save")
    public ResponseEntity<DrawIODiagramResp> saveDiagram(
            @Validated @RequestBody DrawIOSaveRequest request,
            HttpServletRequest httpRequest) {
        logger.info("接收到保存图表请求 - 名称: {}", request.getName());
        Long userId = getUserId(httpRequest);
        DrawIODiagramResp response = drawIOService.saveDiagram(request, userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取图表列表
     */
    @Operation(summary = "获取图表列表")
    @GetMapping("/list")
    public ResponseEntity<List<DrawIODiagramResp>> getDiagramList(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        List<DrawIODiagramResp> response = drawIOService.getDiagramList(userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取图表详情
     */
    @Operation(summary = "获取图表详情")
    @GetMapping("/{id}")
    public ResponseEntity<DrawIODiagramResp> getDiagramDetail(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        DrawIODiagramResp response = drawIOService.getDiagramDetail(id, userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 删除图表
     */
    @Operation(summary = "删除图表")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDiagram(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        drawIOService.deleteDiagram(id, userId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 保存历史记录
     */
    @Operation(summary = "保存历史记录")
    @PostMapping("/history")
    public ResponseEntity<DrawIOHistoryResp> saveHistory(
            @Validated @RequestBody DrawIOHistoryRequest request,
            HttpServletRequest httpRequest) {
        logger.info("接收到保存历史记录请求 - 提示词: {}", request.getPrompt());
        Long userId = getUserId(httpRequest);
        DrawIOHistoryResp response = drawIOService.saveHistory(request, userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取历史记录列表
     */
    @Operation(summary = "获取历史记录列表")
    @GetMapping("/history")
    public ResponseEntity<List<DrawIOHistoryResp>> getHistoryList(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        List<DrawIOHistoryResp> response = drawIOService.getHistoryList(userId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 删除历史记录
     */
    @Operation(summary = "删除历史记录")
    @DeleteMapping("/history/{id}")
    public ResponseEntity<Void> deleteHistory(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        drawIOService.deleteHistory(id, userId);
        return ResponseEntity.ok().build();
    }
}

