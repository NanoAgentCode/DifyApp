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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Skill management")
@RestController
public class AgentSkillController extends BaseController {

    @Autowired
    private AgentSkillService agentSkillService;

    @Operation(summary = "List admin skills")
    @GetMapping("/api/admin/skills")
    public ResponseEntity<List<Map<String, Object>>> listAdminSkills(HttpServletRequest request) {
        checkAdmin(request);
        return ResponseEntity.ok(agentSkillService.listAdminSkills());
    }

    @Operation(summary = "Get admin skill detail")
    @GetMapping("/api/admin/skills/{skillKey}")
    public ResponseEntity<Map<String, Object>> getSkillDetail(@PathVariable String skillKey, HttpServletRequest request) {
        checkAdmin(request);
        return ResponseEntity.ok(agentSkillService.getAdminSkillDetail(skillKey));
    }

    @Operation(summary = "Update skill config")
    @PutMapping("/api/admin/skills/{skillKey}")
    public ResponseEntity<Map<String, Object>> updateSkill(
            @PathVariable String skillKey,
            @RequestBody AgentSkillUpdateRequest req,
            HttpServletRequest request) {
        Long userId = checkAdmin(request);
        String username = getUsername(request);
        Map<String, Object> data = agentSkillService.updateSkill(
                skillKey,
                req.getSkillName(),
                req.getEnabled(),
                req.getVisibleToUser(),
                req.getDescription(),
                req.getExtJson(),
                userId,
                username);
        return ResponseEntity.ok(data);
    }

    @Operation(summary = "Sync skills from skill directory")
    @PostMapping("/api/admin/skills/sync")
    public ResponseEntity<Map<String, Object>> syncSkills(HttpServletRequest request) {
        checkAdmin(request);
        int synced = agentSkillService.syncSkills();
        Map<String, Object> body = new HashMap<>();
        body.put("syncedCount", synced);
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Delete skill config")
    @DeleteMapping("/api/admin/skills/{skillKey}")
    public ResponseEntity<Void> deleteSkill(@PathVariable String skillKey, HttpServletRequest request) {
        checkAdmin(request);
        agentSkillService.deleteSkill(skillKey);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List available skills for current role")
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
            throw new UnauthorizedException("Only admin can access this api", ErrorCode.UNAUTHORIZED);
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
