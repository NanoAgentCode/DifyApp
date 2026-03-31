package com.github.app.dify.system.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.UnauthorizedException;
import com.github.app.dify.system.service.AgentSkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "技能管理")
@RestController
public class AgentSkillController extends BaseController {

    @Autowired
    private AgentSkillService agentSkillService;

    @Operation(summary = "管理端获取技能配置列表")
    @GetMapping("/api/admin/skills")
    public ResponseEntity<List<Map<String, Object>>> listAdminSkills(HttpServletRequest request) {
        checkAdmin(request);
        return ResponseEntity.ok(agentSkillService.listAdminSkills());
    }

    @Operation(summary = "管理端更新技能配置")
    @PutMapping("/api/admin/skills/{skillKey}")
    public ResponseEntity<Map<String, Object>> updateSkill(
            @PathVariable String skillKey,
            @RequestBody AgentSkillUpdateRequest req,
            HttpServletRequest request) {
        Long userId = checkAdmin(request);
        String username = getUsername(request);
        Map<String, Object> data = agentSkillService.updateSkill(
                skillKey,
                req.getEnabled(),
                req.getVisibleToUser(),
                req.getDescription(),
                userId,
                username);
        return ResponseEntity.ok(data);
    }

    @Operation(summary = "管理端同步技能目录")
    @PostMapping("/api/admin/skills/sync")
    public ResponseEntity<Map<String, Object>> syncSkills(HttpServletRequest request) {
        checkAdmin(request);
        int synced = agentSkillService.syncSkills();
        Map<String, Object> body = new HashMap<>();
        body.put("syncedCount", synced);
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "管理端删除技能配置")
    @DeleteMapping("/api/admin/skills/{skillKey}")
    public ResponseEntity<Void> deleteSkill(@PathVariable String skillKey, HttpServletRequest request) {
        checkAdmin(request);
        agentSkillService.deleteSkill(skillKey);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "获取当前角色可用技能列表")
    @GetMapping("/api/skills/available")
    public ResponseEntity<List<Map<String, Object>>> availableSkills(
            @RequestParam(value = "forRole", required = false) String forRole,
            HttpServletRequest request) {
        getUserId(request);
        String actualRole = isAdmin(request) ? "admin" : "user";
        String targetRole = normalizeRole(forRole, actualRole);
        return ResponseEntity.ok(agentSkillService.listAvailableSkills(targetRole));
    }

    private Long checkAdmin(HttpServletRequest request) {
        Long userId = getUserId(request);
        if (!isAdmin(request)) {
            throw new UnauthorizedException("需要管理员权限", ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private boolean isAdmin(HttpServletRequest request) {
        Integer role = getRole(request);
        return role != null && role == 1;
    }

    private String normalizeRole(String requestedRole, String actualRole) {
        if ("admin".equalsIgnoreCase(actualRole)) {
            if ("user".equalsIgnoreCase(requestedRole)) {
                return "user";
            }
            return "admin";
        }
        return "user";
    }
}
