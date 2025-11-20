package com.github.app.dify.controller;

import com.github.app.dify.req.ChangePasswordRequest;
import com.github.app.dify.req.LoginRequest;
import com.github.app.dify.req.RegisterRequest;
import com.github.app.dify.req.ResetPasswordRequest;
import com.github.app.dify.resp.LoginResponse;
import com.github.app.dify.resp.RegisterResponse;
import com.github.app.dify.resp.UserAppVisibilityResp;
import com.github.app.dify.service.AuthService;
import com.github.app.dify.service.UserAppVisibilityService;
import com.github.app.dify.util.JwtUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 认证控制器
 */
@Api(tags = "用户认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private UserAppVisibilityService userAppVisibilityService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * 用户注册
     */
    @ApiOperation("用户注册")
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Validated @RequestBody RegisterRequest request) {
        try {
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
    @ApiOperation("用户登录")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Validated @RequestBody LoginRequest request) {
        try {
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
    @ApiOperation("管理员审核用户（激活用户）")
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
    @ApiOperation("管理员禁用用户")
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
    @ApiOperation("获取所有用户列表（管理员使用）")
    @GetMapping("/users")
    public ResponseEntity<java.util.List<com.github.app.dify.resp.UserResp>> getAllUsers() {
        try {
            java.util.List<com.github.app.dify.resp.UserResp> users = authService.getAllUsers();
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("获取用户列表失败", e);
            throw e;
        }
    }
    
    /**
     * 修改密码
     */
    @ApiOperation("修改密码")
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
                throw new RuntimeException("无法获取用户信息，请重新登录");
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
    @ApiOperation("管理员重置用户密码")
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
    @ApiOperation("获取用户的应用可见性列表")
    @GetMapping("/users/{userId}/app-visibilities")
    public ResponseEntity<java.util.List<UserAppVisibilityResp>> getUserAppVisibilities(@PathVariable Long userId) {
        try {
            java.util.List<UserAppVisibilityResp> visibilities = userAppVisibilityService.getUserAppVisibilities(userId);
            return ResponseEntity.ok(visibilities);
        } catch (Exception e) {
            logger.error("获取用户应用可见性列表失败", e);
            throw e;
        }
    }
    
    /**
     * 更新用户对应用的可见性
     */
    @ApiOperation("更新用户对应用的可见性")
    @PutMapping("/users/{userId}/app-visibilities/{appId}")
    public ResponseEntity<Void> updateUserAppVisibility(
            @PathVariable Long userId,
            @PathVariable Long appId,
            @RequestParam Boolean visible) {
        try {
            userAppVisibilityService.updateUserAppVisibility(userId, appId, visible);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("更新用户应用可见性失败", e);
            throw e;
        }
    }
}

