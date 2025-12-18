package com.github.app.dify.chat.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.chat.req.ChatHistoryRequest;
import com.github.app.dify.chat.req.CreateConversationRequest;
import com.github.app.dify.chat.resp.*;
import com.github.app.dify.chat.service.ChatHistoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 会话历史管理控制器（用户端）
 * 会话（Conversation）：一个完整的对话会话，包含多轮问答
 * 消息（Message）：会话中的单条消息，一问一答为一轮对话
 */
@Tag(name = "会话历史管理")
@RestController
@RequestMapping("/api/chat/history")
public class ChatHistoryController extends BaseController {
    
    @Autowired
    private ChatHistoryService chatHistoryService;
    
    /**
     * 检查是否为管理员
     */
    private boolean isAdmin(HttpServletRequest request) {
        Object roleObj = request.getAttribute("role");
        return roleObj instanceof Integer && (Integer) roleObj == 1;
    }
    
    /**
     * 创建新会话
     */
    @Operation(summary = "创建新会话")
    @PostMapping("/conversations")
    public ResponseEntity<ChatConversationResponse> createConversation(
            @Validated @RequestBody CreateConversationRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        ChatConversationResponse response = chatHistoryService.createConversation(userId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取我的会话列表（用户端）
     */
    @Operation(summary = "获取我的会话列表")
    @GetMapping("/conversations")
    public ResponseEntity<com.github.app.dify.common.resp.PageResponse<ChatConversationResponse>> getMyConversations(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer type,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        
        // 构建请求对象
        ChatHistoryRequest request = new ChatHistoryRequest();
        request.setPage(page);
        request.setSize(size);
        request.setKeyword(keyword);
        request.setType(type);
        
        com.github.app.dify.common.resp.PageResponse<ChatConversationResponse> response =
                chatHistoryService.getMyConversations(userId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取会话详情
     */
    @Operation(summary = "获取会话详情")
    @GetMapping("/conversations/{id}")
    public ResponseEntity<ChatConversationResponse> getConversation(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        
        ChatConversationResponse response = chatHistoryService.getConversation(id, userId, isAdmin);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取会话消息列表（该会话中的所有对话消息）
     */
    @Operation(summary = "获取会话消息列表")
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        
        List<ChatMessageResponse> response = chatHistoryService.getMessages(id, userId, isAdmin);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 更新会话标题
     */
    @Operation(summary = "更新会话标题")
    @PutMapping("/conversations/{id}/title")
    public ResponseEntity<Void> updateConversationTitle(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        
        String title = request.get("title");
        if (title == null || title.trim().isEmpty()) {
            throw new com.github.app.dify.common.exception.BusinessException("标题不能为空");
        }
        
        chatHistoryService.updateConversationTitle(id, userId, title, isAdmin);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 删除会话（会删除该会话中的所有消息）
     */
    @Operation(summary = "删除会话")
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        
        chatHistoryService.deleteConversation(id, userId, isAdmin);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 导出会话（包含该会话中的所有消息）
     */
    @Operation(summary = "导出会话")
    @GetMapping("/conversations/{id}/export")
    public ResponseEntity<Map<String, Object>> exportConversation(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        boolean isAdmin = isAdmin(httpRequest);
        
        Map<String, Object> export = chatHistoryService.exportConversation(id, userId, isAdmin);
        return ResponseEntity.ok(export);
    }
}