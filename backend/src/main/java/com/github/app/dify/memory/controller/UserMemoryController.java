package com.github.app.dify.memory.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.memory.resp.UserMemoryItemResp;
import com.github.app.dify.memory.service.UserMemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "用户-记忆管理")
@RestController
@RequestMapping("/api/memory")
public class UserMemoryController extends BaseController {

    @Autowired
    private UserMemoryService userMemoryService;

    @Operation(summary = "查看我的记忆")
    @GetMapping("/items")
    public ResponseEntity<List<UserMemoryItemResp>> listMyMemory(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) Long scopeId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            HttpServletRequest request
    ) {
        Long userId = getUserId(request);
        List<UserMemoryItemResp> resp = userMemoryService.listUserMemory(
                userId,
                type,
                page != null ? page : 1,
                size != null ? size : 10,
                scopeType,
                scopeId
        );
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "清空我的记忆")
    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearMyMemory(
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) Long scopeId,
            HttpServletRequest request
    ) {
        Long userId = getUserId(request);
        userMemoryService.clearUserMemory(userId, scopeType, scopeId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "删除我的记忆条目")
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> deleteMyMemoryItem(@PathVariable Long id, HttpServletRequest request) {
        Long userId = getUserId(request);
        userMemoryService.deleteUserMemoryItem(userId, id);
        return ResponseEntity.ok().build();
    }
}

