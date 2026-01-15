package com.github.app.dify.auth.controller;

import com.github.app.dify.auth.req.ChangePasswordRequest;
import com.github.app.dify.auth.req.LoginRequest;
import com.github.app.dify.auth.req.RegisterRequest;
import com.github.app.dify.auth.req.ResetPasswordRequest;
import com.github.app.dify.auth.resp.LoginResponse;
import com.github.app.dify.auth.resp.RegisterResponse;
import com.github.app.dify.auth.resp.UserResp;
import com.github.app.dify.auth.service.AuthService;
import com.github.app.dify.auth.util.JwtUtil;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.permission.resp.UserAppVisibilityResp;
import com.github.app.dify.permission.resp.UserDataSourceVisibilityResp;
import com.github.app.dify.permission.resp.UserKnowledgeBaseVisibilityResp;
import com.github.app.dify.permission.service.UserAppVisibilityService;
import com.github.app.dify.permission.service.UserDataSourceVisibilityService;
import com.github.app.dify.permission.service.UserKnowledgeBaseVisibilityService;
import com.github.app.dify.userlog.annotation.UserAction;
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
/**
 * 认证控制器
 */
@Tag(name = "用户认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private AuthService authService;
    
    @Autowired(required = false)
    private UserAppVisibilityService userAppVisibilityService;
    
    @Autowired(required = false)
    private UserKnowledgeBaseVisibilityService userKnowledgeBaseVisibilityService;
    
    @Autowired(required = false)
    private UserDataSourceVisibilityService userDataSourceVisibilityService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * 用户注册
     */
    @UserAction(module = "用户管理", actionType = "用户注册", description = "用户注册")
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(
            @Validated @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        try {
            // 注册成功后需要记录日志，但用户还没登录，所以先注册再记录
            RegisterResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("用户注册失败", e);
            throw e;
        }
    }
    
    /**
     * 用户登录
     */
    @UserAction(module = "用户管理", actionType = "用户登录", description = "用户登录", logParams = false)
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Validated @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        try {
            // 先查询用户，即使登录失败也要记录用户名
            com.github.app.dify.auth.domain.User user = null;
            try {
                user = authService.getUserByUsername(request.getUsername());
            } catch (Exception e) {
                // 用户不存在，忽略，稍后会在login中抛出异常
            }
            
            // 立即设置用户信息到request（在AOP获取用户信息之前）
            if (user != null) {
                httpRequest.setAttribute("userId", user.getId());
                httpRequest.setAttribute("username", user.getUsername());
                logger.info("在方法开始时设置用户信息到request - userId: {}, username: {}", user.getId(), user.getUsername());
            }
            
            // 执行登录逻辑
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("用户登录失败", e);
            throw e;
        }
    }
    
    /**
     * 管理员审核用户（激活用户）
     */
    @UserAction(module = "用户管理", actionType = "审核用户", description = "管理员审核并激活用户")
    @Operation(summary = "管理员审核用户（激活用户）")
    @PostMapping("/approve/{userId}")
    public ResponseEntity<Void> approveUser(@PathVariable Long userId) {
        try {
            authService.approveUser(userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("审核用户失败", e);
            throw e;
        }
    }
    
    /**
     * 管理员禁用用户
     */
    @UserAction(module = "用户管理", actionType = "禁用用户", description = "管理员禁用用户")
    @Operation(summary = "管理员禁用用户")
    @PostMapping("/disable/{userId}")
    public ResponseEntity<Void> disableUser(@PathVariable Long userId) {
        try {
            authService.disableUser(userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("禁用用户失败", e);
            throw e;
        }
    }
    
    /**
     * 获取所有用户列表（管理员使用）
     */
    @Operation(summary = "获取所有用户列表（管理员使用）")
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        try {
            // 如果指定了分页参数，使用分页接口
            if (page != null && pageSize != null && page > 0 && pageSize > 0) {
                PageResponse<UserResp> pageResponse = 
                        authService.getAllUsersWithPagination(keyword, status, role, page, pageSize);
                return ResponseEntity.ok(pageResponse);
            } else {
                // 否则返回所有数据（兼容旧接口）
                List<UserResp> users = authService.getAllUsers(keyword, status, role);
                return ResponseEntity.ok(users);
            }
        } catch (Exception e) {
            logger.error("获取用户列表失败", e);
            throw e;
        }
    }
    
    /**
     * 修改密码
     */
    @UserAction(module = "用户管理", actionType = "修改密码", description = "用户修改密码", logParams = false)
    @Operation(summary = "修改密码")
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Validated @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest httpRequest) {
        try {
            // 从Token中获取用户ID
            Long userId = null;
            if (authorization != null && authorization.startsWith("Bearer ")) {
                String token = authorization.substring(7);
                userId = jwtUtil.getUserIdFromToken(token);
            } else {
                // 如果Header中没有，尝试从request attribute中获取（JWT拦截器设置的）
                Object userIdObj = httpRequest.getAttribute("userId");
                if (userIdObj instanceof Long) {
                    userId = (Long) userIdObj;
                } else if (userIdObj instanceof Integer) {
                    userId = ((Integer) userIdObj).longValue();
                }
            }
            
            if (userId == null) {
                throw new BusinessException("未授权，请重新登录", ErrorCode.UNAUTHORIZED);
            }
            
            authService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("修改密码失败", e);
            throw e;
        }
    }
    
    /**
     * 管理员重置用户密码
     */
    @UserAction(module = "用户管理", actionType = "重置密码", description = "管理员重置用户密码", logParams = false)
    @Operation(summary = "管理员重置用户密码")
    @PostMapping("/reset-password/{userId}")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long userId,
            @Validated @RequestBody ResetPasswordRequest request) {
        try {
            authService.resetPassword(userId, request.getNewPassword());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("重置用户密码失败", e);
            throw e;
        }
    }
    
    /**
     * 获取用户的应用可见性列表
     */
    @Operation(summary = "获取用户的应用可见性列表")
    @GetMapping("/users/{userId}/app-visibilities")
    public ResponseEntity<List<UserAppVisibilityResp>> getUserAppVisibilities(@PathVariable Long userId) {
        try {
            if (userAppVisibilityService == null) {
                return ResponseEntity.ok(new java.util.ArrayList<>());
            }
            List<UserAppVisibilityResp> visibilities = userAppVisibilityService.getUserAppVisibilities(userId);
            return ResponseEntity.ok(visibilities);
        } catch (Exception e) {
            logger.error("获取用户应用可见性列表失败", e);
            throw e;
        }
    }
    
    /**
     * 更新用户对应用的可见性
     */
    @Operation(summary = "更新用户对应用的可见性")
    @PutMapping("/users/{userId}/app-visibilities/{appId}")
    public ResponseEntity<Void> updateUserAppVisibility(
            @PathVariable Long userId,
            @PathVariable Long appId,
            @RequestParam Boolean visible) {
        try {
            if (userAppVisibilityService == null) {
                return ResponseEntity.ok().build();
            }
            userAppVisibilityService.updateUserAppVisibility(userId, appId, visible);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("更新用户应用可见性失败", e);
            throw e;
        }
    }
    
    /**
     * 更新用户角色
     */
    @UserAction(module = "用户管理", actionType = "更新用户角色", description = "管理员更新用户角色")
    @Operation(summary = "更新用户角色")
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<Void> updateUserRole(
            @PathVariable Long userId,
            @RequestParam Integer role) {
        try {
            authService.updateUserRole(userId, role);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("更新用户角色失败", e);
            throw e;
        }
    }
    
    /**
     * 获取用户的知识库可见性列表
     */
    @Operation(summary = "获取用户的知识库可见性列表")
    @GetMapping("/users/{userId}/knowledge-base-visibilities")
    public ResponseEntity<List<UserKnowledgeBaseVisibilityResp>> getUserKnowledgeBaseVisibilities(
            @PathVariable Long userId) {
        try {
            if (userKnowledgeBaseVisibilityService == null) {
                return ResponseEntity.ok(new java.util.ArrayList<>());
            }
            List<UserKnowledgeBaseVisibilityResp> resp = 
                    userKnowledgeBaseVisibilityService.getUserKnowledgeBaseVisibilities(userId);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("获取用户知识库可见性列表失败", e);
            throw e;
        }
    }
    
    /**
     * 更新用户对知识库的可见性
     */
    @Operation(summary = "更新用户对知识库的可见性")
    @PutMapping("/users/{userId}/knowledge-base-visibilities/{knowledgeBaseId}")
    public ResponseEntity<Void> updateUserKnowledgeBaseVisibility(
            @PathVariable Long userId,
            @PathVariable Long knowledgeBaseId,
            @RequestParam Boolean visible) {
        try {
            if (userKnowledgeBaseVisibilityService == null) {
                return ResponseEntity.ok().build();
            }
            userKnowledgeBaseVisibilityService.updateUserKnowledgeBaseVisibility(userId, knowledgeBaseId, visible);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("更新用户知识库可见性失败", e);
            throw e;
        }
    }
    
    /**
     * 获取用户的数据源可见性列表
     */
    @Operation(summary = "获取用户的数据源可见性列表")
    @GetMapping("/users/{userId}/data-source-visibilities")
    public ResponseEntity<?> getUserDataSourceVisibilities(@PathVariable Long userId) {
        try {
            if (userDataSourceVisibilityService == null) {
                return ResponseEntity.ok(new java.util.ArrayList<>());
            }
            List<UserDataSourceVisibilityResp> resp =
                    userDataSourceVisibilityService.getUserDataSourceVisibilities(userId);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("获取用户数据源可见性列表失败", e);
            throw e;
        }
    }
    
    /**
     * 更新用户对数据源的可见性
     */
    @Operation(summary = "更新用户对数据源的可见性")
    @PutMapping("/users/{userId}/data-source-visibilities/{dataSourceId}")
    public ResponseEntity<Void> updateUserDataSourceVisibility(
            @PathVariable Long userId,
            @PathVariable Long dataSourceId,
            @RequestParam Boolean visible) {
        try {
            if (userDataSourceVisibilityService == null) {
                return ResponseEntity.ok().build();
            }
            userDataSourceVisibilityService.updateUserDataSourceVisibility(userId, dataSourceId, visible);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("更新用户数据源可见性失败", e);
            throw e;
        }
    }
    
    /**
     * 批量更新用户对数据源的可见性
     */
    @Operation(summary = "批量更新用户对数据源的可见性")
    @PutMapping("/users/{userId}/data-source-visibilities/batch")
    public ResponseEntity<Void> batchUpdateUserDataSourceVisibility(
            @PathVariable Long userId,
            @RequestBody java.util.Map<String, Object> request) {
        try {
            if (userDataSourceVisibilityService == null) {
                return ResponseEntity.ok().build();
            }
            @SuppressWarnings("unchecked")
            List<Long> dataSourceIds = (List<Long>) request.get("dataSourceIds");
            Boolean visible = (Boolean) request.get("visible");
            if (dataSourceIds != null && visible != null) {
                userDataSourceVisibilityService.batchUpdateUserDataSourceVisibility(userId, dataSourceIds, visible);
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("批量更新用户数据源可见性失败", e);
            throw e;
        }
    }
}
