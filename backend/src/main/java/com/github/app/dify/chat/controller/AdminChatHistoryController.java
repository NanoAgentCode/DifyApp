package com.github.app.dify.chat.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.ForbiddenException;
import com.github.app.dify.chat.req.ChatHistoryRequest;
import com.github.app.dify.chat.resp.*;
import com.github.app.dify.chat.service.ChatHistoryService;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.ops.userlog.annotation.UserAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
public class AdminChatHistoryController extends BaseController {
    
    @Autowired
    private ChatHistoryService chatHistoryService;
    
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
        checkAdmin(httpRequest);
        
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
    }
    
    /**
     * 获取统计信息
     */
    @Operation(summary = "获取统计信息")
    @GetMapping("/statistics")
    public ResponseEntity<ChatHistoryStatisticsResponse> getStatistics(
            @RequestParam(required = false) Integer days,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        ChatHistoryStatisticsResponse response = chatHistoryService.getStatistics(days);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 删除会话（管理员，会删除该会话中的所有消息）
     */
    @UserAction(module = "对话管理", actionType = "删除", description = "删除对话会话")
    @Operation(summary = "删除会话（管理员）")
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        Long userId = getUserId(httpRequest);
        chatHistoryService.deleteConversation(id, userId, true);
        return ResponseEntity.ok().build();
    }
    
    /**
     * 批量删除会话（管理员，会删除这些会话中的所有消息）
     */
    @UserAction(module = "对话管理", actionType = "批量删除", description = "批量删除对话会话")
    @Operation(summary = "批量删除会话")
    @DeleteMapping("/conversations/batch")
    public ResponseEntity<Void> batchDeleteConversations(
            @RequestBody Map<String, List<Long>> request,
            HttpServletRequest httpRequest) {
        checkAdmin(httpRequest);
        
        List<Long> conversationIds = request.get("ids");
        if (conversationIds == null || conversationIds.isEmpty()) {
            throw new com.github.app.dify.common.exception.BusinessException("会话ID列表不能为空");
        }
        
        chatHistoryService.batchDeleteConversations(conversationIds);
        return ResponseEntity.ok().build();
    }
}