package com.github.app.dify.service;

import com.github.app.dify.domain.User;
import com.github.app.dify.req.LoginRequest;
import com.github.app.dify.req.RegisterRequest;
import com.github.app.dify.resp.LoginResponse;
import com.github.app.dify.resp.PageResponse;
import com.github.app.dify.resp.RegisterResponse;
import com.github.app.dify.resp.UserResp;

import java.util.List;

/**
 * 认证服务接口
 */
public interface AuthService {
    
    /**
     * 用户注册
     * 普通用户注册后状态为待审核（0），需要管理员审核后才能登录
     */
    RegisterResponse register(RegisterRequest request);
    
    /**
     * 用户登录
     * 只有状态为已激活（1）的用户才能登录
     */
    LoginResponse login(LoginRequest request);
    
    /**
     * 管理员审核用户（激活用户）
     */
    void approveUser(Long userId);
    
    /**
     * 管理员禁用用户
     * 注意：管理员账号不能被禁用
     */
    void disableUser(Long userId);
    
    /**
     * 根据用户ID获取用户信息
     */
    User getUserById(Long userId);
    
    /**
     * 根据用户名获取用户信息
     */
    User getUserByUsername(String username);
    
    /**
     * 获取所有用户列表（管理员使用）
     */
    List<UserResp> getAllUsers(String keyword, Integer status, Integer role);
    
    /**
     * 获取所有用户列表（分页，管理员使用）
     */
    PageResponse<UserResp> getAllUsersWithPagination(String keyword, Integer status, Integer role, int page, int pageSize);
    
    /**
     * 修改密码
     * @param userId 用户ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    void changePassword(Long userId, String oldPassword, String newPassword);
    
    /**
     * 管理员重置用户密码（不需要原密码）
     * @param userId 用户ID
     * @param newPassword 新密码
     */
    void resetPassword(Long userId, String newPassword);
    
    /**
     * 更新用户角色
     * 注意：超级管理员（username为"admin"或id为1）的角色不能被修改
     * @param userId 用户ID
     * @param role 新角色：1-管理员，2-普通用户
     */
    void updateUserRole(Long userId, Integer role);
}
