package com.github.app.dify.controller;

import com.github.app.dify.req.ChatHistoryRequest;
import com.github.app.dify.req.CreateConversationRequest;
import com.github.app.dify.resp.*;
import com.github.app.dify.service.ChatHistoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class ChatHistoryController {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatHistoryController.class);
    
    @Autowired
    private ChatHistoryService chatHistoryService;
    
    /**
     * 创建新会话
     */
    @Operation(summary = "创建新会话")
    @PostMapping("/conversations")
    public ResponseEntity<ChatConversationResponse> createConversation(
            @Validated @RequestBody CreateConversationRequest request,
            HttpServletRequest httpRequest) {
        try {
            Long userId = (Long) httpRequest.getAttribute("userId");
            if (userId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            ChatConversationResponse response = chatHistoryService.createConversation(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("创建对话失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取我的会话列表（用户端）
     */
    @Operation(summary = "获取我的会话列表")
    @GetMapping("/conversations")
    public ResponseEntity<PageResponse<ChatConversationResponse>> getMyConversations(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer type,
            HttpServletRequest httpRequest) {
        try {
            Long userId = (Long) httpRequest.getAttribute("userId");
            if (userId == null) {
                return ResponseEntity.badRequest().build();
            }
            
            // 构建请求对象
            ChatHistoryRequest request = new ChatHistoryRequest();
            request.setPage(page);
            request.setSize(size);
            request.setKeyword(keyword);
            request.setType(type);
            
            PageResponse<ChatConversationResponse> response = 
                    chatHistoryService.getMyConversations(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取对话列表失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取会话详情
     */
    @Operation(summary = "获取会话详情")
    @GetMapping("/conversations/{id}")
    public ResponseEntity<ChatConversationResponse> getConversation(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        try {
            Long userId = (Long) httpRequest.getAttribute("userId");
            Integer role = (Integer) httpRequest.getAttribute("role");
            boolean isAdmin = (role != null && role == 1);
            
            ChatConversationResponse response = chatHistoryService.getConversation(id, userId, isAdmin);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取对话详情失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取会话消息列表（该会话中的所有对话消息）
     */
    @Operation(summary = "获取会话消息列表")
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        try {
            Long userId = (Long) httpRequest.getAttribute("userId");
            Integer role = (Integer) httpRequest.getAttribute("role");
            boolean isAdmin = (role != null && role == 1);
            
            List<ChatMessageResponse> response = chatHistoryService.getMessages(id, userId, isAdmin);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取消息列表失败", e);
            return ResponseEntity.badRequest().build();
        }
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
        try {
            Long userId = (Long) httpRequest.getAttribute("userId");
            Integer role = (Integer) httpRequest.getAttribute("role");
            boolean isAdmin = (role != null && role == 1);
            
            String title = request.get("title");
            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            chatHistoryService.updateConversationTitle(id, userId, title, isAdmin);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("更新对话标题失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 删除会话（会删除该会话中的所有消息）
     */
    @Operation(summary = "删除会话")
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        try {
            Long userId = (Long) httpRequest.getAttribute("userId");
            Integer role = (Integer) httpRequest.getAttribute("role");
            boolean isAdmin = (role != null && role == 1);
            
            chatHistoryService.deleteConversation(id, userId, isAdmin);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("删除对话失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 导出会话（包含该会话中的所有消息）
     */
    @Operation(summary = "导出会话")
    @GetMapping("/conversations/{id}/export")
    public ResponseEntity<Map<String, Object>> exportConversation(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        try {
            Long userId = (Long) httpRequest.getAttribute("userId");
            Integer role = (Integer) httpRequest.getAttribute("role");
            boolean isAdmin = (role != null && role == 1);
            
            Map<String, Object> export = chatHistoryService.exportConversation(id, userId, isAdmin);
            return ResponseEntity.ok(export);
        } catch (Exception e) {
            logger.error("导出对话失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
}