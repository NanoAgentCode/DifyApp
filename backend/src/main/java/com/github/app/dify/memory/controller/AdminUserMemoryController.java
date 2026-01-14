package com.github.app.dify.memory.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.ForbiddenException;
import com.github.app.dify.memory.service.UserMemoryService;
import com.github.app.dify.memory.resp.UserMemoryItemResp;
import com.github.app.dify.userlog.annotation.UserAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理员-用户记忆管理")
@RestController
@RequestMapping("/api/admin/memory")
public class AdminUserMemoryController extends BaseController {

    @Autowired
    private UserMemoryService userMemoryService;

    private void checkAdmin(HttpServletRequest request) {
        Object roleObj = request.getAttribute("role");
        if (!(roleObj instanceof Integer) || (Integer) roleObj != 1) {
            throw new ForbiddenException("需要管理员权限");
        }
    }

    @UserAction(module = "用户管理", actionType = "清空用户记忆", description = "管理员清空用户的长期记忆与实体记忆")
    @Operation(summary = "清空用户记忆")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> clearUserMemory(
            @PathVariable Long userId,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) Long scopeId,
            HttpServletRequest request
    ) {
        checkAdmin(request);
        userMemoryService.clearUserMemory(userId, scopeType, scopeId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "查看用户记忆")
    @GetMapping("/users/{userId}/items")
    public ResponseEntity<List<UserMemoryItemResp>> listUserMemory(
            @PathVariable Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) Long scopeId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer size,
            HttpServletRequest request) {
        checkAdmin(request);
        List<UserMemoryItemResp> resp = userMemoryService.listUserMemory(
                userId,
                type,
                page != null ? page : 1,
                size != null ? size : 50,
                scopeType,
                scopeId
        );
        return ResponseEntity.ok(resp);
    }
}
