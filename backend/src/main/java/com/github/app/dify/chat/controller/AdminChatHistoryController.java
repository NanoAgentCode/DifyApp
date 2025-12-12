package com.github.app.dify.chat.controller;

import com.github.app.dify.chat.req.ChatHistoryRequest;
import com.github.app.dify.chat.resp.*;
import com.github.app.dify.chat.service.ChatHistoryService;
import com.github.app.dify.common.resp.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;
/**
 * 管理员会话历史管理控制器
 * 会话（Conversation）：一个完整的对话会话，包含多轮问答
 * 消息（Message）：会话中的单条消息，一问一答为一轮对话
 */
@Tag(name = "管理员-会话历史管理")
@RestController
@RequestMapping("/api/admin/chat/history")
public class AdminChatHistoryController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminChatHistoryController.class);
    
    @Autowired
    private ChatHistoryService chatHistoryService;
    
    /**
     * 获取所有会话列表（管理员端）
     */
    @Operation(summary = "获取所有会话列表")
    @GetMapping("/conversations")
    public ResponseEntity<PageResponse<ChatConversationResponse>> getAllConversations(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Date endTime,
            HttpServletRequest httpRequest) {
        try {
            Integer role = (Integer) httpRequest.getAttribute("role");
            if (role == null || role != 1) {
                return ResponseEntity.status(403).build(); // 非管理员
            }
            
            // 构建请求对象
            ChatHistoryRequest request = new ChatHistoryRequest();
            request.setPage(page);
            request.setSize(size);
            request.setKeyword(keyword);
            request.setType(type);
            request.setUserId(userId);
            request.setStartTime(startTime);
            request.setEndTime(endTime);
            
            PageResponse<ChatConversationResponse> response = 
                    chatHistoryService.getAllConversations(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取对话列表失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取统计信息
     */
    @Operation(summary = "获取统计信息")
    @GetMapping("/statistics")
    public ResponseEntity<ChatHistoryStatisticsResponse> getStatistics(
            HttpServletRequest httpRequest) {
        try {
            Integer role = (Integer) httpRequest.getAttribute("role");
            if (role == null || role != 1) {
                return ResponseEntity.status(403).build(); // 非管理员
            }
            
            ChatHistoryStatisticsResponse response = chatHistoryService.getStatistics();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取统计信息失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 删除会话（管理员，会删除该会话中的所有消息）
     */
    @Operation(summary = "删除会话（管理员）")
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        try {
            Integer role = (Integer) httpRequest.getAttribute("role");
            if (role == null || role != 1) {
                return ResponseEntity.status(403).build(); // 非管理员
            }
            
            Long userId = (Long) httpRequest.getAttribute("userId");
            chatHistoryService.deleteConversation(id, userId, true);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("删除对话失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 批量删除会话（管理员，会删除这些会话中的所有消息）
     */
    @Operation(summary = "批量删除会话")
    @DeleteMapping("/conversations/batch")
    public ResponseEntity<Void> batchDeleteConversations(
            @RequestBody Map<String, List<Long>> request,
            HttpServletRequest httpRequest) {
        try {
            Integer role = (Integer) httpRequest.getAttribute("role");
            if (role == null || role != 1) {
                return ResponseEntity.status(403).build(); // 非管理员
            }
            
            List<Long> conversationIds = request.get("ids");
            if (conversationIds == null || conversationIds.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            chatHistoryService.batchDeleteConversations(conversationIds);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("批量删除对话失败", e);
            return ResponseEntity.badRequest().build();
        }
    }
}