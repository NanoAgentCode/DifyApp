package com.github.app.dify.auth.service.impl;

import com.github.app.dify.auth.domain.User;
import com.github.app.dify.auth.repository.UserRepository;
import com.github.app.dify.auth.req.LoginRequest;
import com.github.app.dify.auth.req.RegisterRequest;
import com.github.app.dify.auth.resp.LoginResponse;
import com.github.app.dify.auth.resp.RegisterResponse;
import com.github.app.dify.auth.resp.UserResp;
import com.github.app.dify.auth.service.AuthService;
import com.github.app.dify.auth.util.JwtUtil;
import com.github.app.dify.auth.util.AuthConverterUtil;
import com.github.app.dify.auth.util.AuthDateTimeUtil;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.permission.service.RbacService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
/**
 * 认证服务实现
 */
@Service
public class AuthServiceImpl implements AuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RbacService rbacService;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    /**
     * 用户注册
     * 普通用户注册后状态为待审核（0），需要管理员审核后才能登录
     */
    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在", ErrorCode.USER_ALREADY_EXISTS);
        }
        
        // 创建新用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(2); // 默认角色为普通用户
        user.setStatus(0); // 状态为待审核
        AuthDateTimeUtil.setCreateAndUpdateTime(user);
        user.setDeleted(0); // 未删除
        
        user = userRepository.save(user);
        
        logger.info("用户注册成功 - 用户名: {}, 用户ID: {}", user.getUsername(), user.getId());
        
        RegisterResponse response = new RegisterResponse();
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setStatus(user.getStatus());
        response.setMessage("注册成功，请等待管理员审核");
        
        return response;
    }
    
    /**
     * 用户登录
     * 只有状态为已激活（1）的用户才能登录
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        // 查找用户
        Optional<User> optional = userRepository.findByUsername(request.getUsername());
        if (optional.isEmpty()) {
            throw new BusinessException("用户名或密码错误", ErrorCode.LOGIN_FAILED);
        }
        
        User user = optional.get();
        
        // 检查用户是否已删除
        if (user.getDeleted() != null && user.getDeleted() == 1) {
            throw new BusinessException("用户名或密码错误", ErrorCode.LOGIN_FAILED);
        }
        
        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误", ErrorCode.LOGIN_FAILED);
        }
        
        // 检查用户状态
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BusinessException("账号待审核，请联系管理员", ErrorCode.FORBIDDEN);
        }
        
        if (user.getStatus() == 2) {
            throw new BusinessException("账号已被禁用，请联系管理员", ErrorCode.FORBIDDEN);
        }
        
        // 生成JWT Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        
        logger.info("用户登录成功 - 用户名: {}, 用户ID: {}, 角色: {}", 
                user.getUsername(), user.getId(), user.getRole());
        
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setUserId(user.getId());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setRoles(rbacService.getUserRoles(user.getId()));
        response.setPermissions(rbacService.getUserPermissionCodes(user.getId()));
        
        return response;
    }
    
    /**
     * 管理员审核用户（激活用户）
     */
    @Override
    @Transactional
    @CacheEvict(value = "user", key = "#userId")
    public void approveUser(Long userId) {
        Optional<User> optional = userRepository.findById(userId);
        if (optional.isEmpty()) {
            throw new BusinessException("用户不存在", ErrorCode.USER_NOT_FOUND);
        }
        
        User user = optional.get();
        user.setStatus(1); // 已激活
        AuthDateTimeUtil.setUpdateTime(user);
        userRepository.save(user);
        
        logger.info("管理员审核通过用户 - 用户ID: {}, 用户名: {}", user.getId(), user.getUsername());
    }
    
    /**
     * 管理员禁用用户
     * 注意：管理员账号不能被禁用
     */
    @Override
    @Transactional
    @CacheEvict(value = "user", key = "#userId")
    public void disableUser(Long userId) {
        Optional<User> optional = userRepository.findById(userId);
        if (optional.isEmpty()) {
            throw new BusinessException("用户不存在", ErrorCode.USER_NOT_FOUND);
        }
        
        User user = optional.get();
        
        // 检查是否为管理员，管理员不能被禁用
        if (user.getRole() != null && user.getRole() == 1) {
            throw new BusinessException("管理员账号不能被禁用", ErrorCode.FORBIDDEN);
        }
        
        user.setStatus(2); // 已禁用
        AuthDateTimeUtil.setUpdateTime(user);
        userRepository.save(user);
        
        logger.info("管理员禁用用户 - 用户ID: {}, 用户名: {}", user.getId(), user.getUsername());
    }
    
    /**
     * 根据用户ID获取用户信息
     */
    @Override
    @Cacheable(value = "user", key = "#userId")
    public User getUserById(Long userId) {
        Optional<User> optional = userRepository.findById(userId);
        if (optional.isEmpty()) {
            throw new BusinessException("用户不存在", ErrorCode.USER_NOT_FOUND);
        }
        return optional.get();
    }
    
    /**
     * 根据用户名获取用户信息
     */
    @Override
    @Cacheable(value = "user", key = "'username:' + #username")
    public User getUserByUsername(String username) {
        Optional<User> optional = userRepository.findByUsername(username);
        if (optional.isEmpty()) {
            throw new BusinessException("用户不存在", ErrorCode.USER_NOT_FOUND);
        }
        return optional.get();
    }
    
    /**
     * 获取所有用户列表（管理员使用）
     * 优化：使用分页查询避免全表加载
     */
    @Override
    @Transactional(readOnly = true)
    public List<UserResp> getAllUsers(String keyword, Integer status, Integer role) {
        List<User> users;
        
        // 如果有关键词或筛选条件，使用搜索方法
        if ((keyword != null && !keyword.trim().isEmpty()) || status != null || role != null) {
            users = userRepository.searchByFilters(
                keyword != null ? keyword.trim() : null, 
                status, 
                role
            );
        } else {
            // 使用分页查询分批加载，避免全表查询
            users = new ArrayList<>();
            int page = 0;
            int batchSize = 500;
            Pageable pageable = PageRequest.of(page, batchSize);
            List<User> batch;
            
            do {
                batch = userRepository.findByDeletedIsNullOrDeleted(pageable).getContent();
                users.addAll(batch);
                page++;
                pageable = PageRequest.of(page, batchSize);
            } while (!batch.isEmpty());
        }
        
        return users.stream()
                .map(AuthConverterUtil::convertToResp)
                .peek(resp -> resp.setRoles(rbacService.getUserRoles(resp.getId())))
                .collect(Collectors.toList());
    }
    
    /**
     * 获取所有用户列表（分页，管理员使用）
     * 优化：使用分页查询避免全表加载
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResp> getAllUsersWithPagination(
            String keyword, Integer status, Integer role, int page, int pageSize) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page - 1, pageSize, org.springframework.data.domain.Sort.by("createTime").descending());
        
        org.springframework.data.domain.Page<User> userPage;
        
        // 如果有关键词或筛选条件，使用搜索方法（已包含删除过滤）
        if ((keyword != null && !keyword.trim().isEmpty()) || status != null || role != null) {
            userPage = userRepository.searchByFiltersWithPagination(
                keyword != null ? keyword.trim() : null, 
                status, 
                role,
                pageable
            );
        } else {
            // 使用分页查询方法，避免全表加载
            userPage = userRepository.findByDeletedIsNullOrDeleted(pageable);
        }
        
        // 搜索方法已经过滤了已删除的用户
        List<UserResp> content = userPage.getContent().stream()
                .map(AuthConverterUtil::convertToResp)
                .peek(resp -> resp.setRoles(rbacService.getUserRoles(resp.getId())))
                .collect(Collectors.toList());
        
        return new PageResponse<>(content, userPage.getTotalElements(), page, pageSize);
    }
    
    /**
     * 修改密码
     * @param userId 用户ID
     * @param oldPassword 原密码
     * @param newPassword 新密码
     */
    @Override
    @Transactional
    @CacheEvict(value = "user", key = "#userId")
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        Optional<User> optional = userRepository.findById(userId);
        if (optional.isEmpty()) {
            throw new BusinessException("用户不存在", ErrorCode.USER_NOT_FOUND);
        }
        
        User user = optional.get();
        
        // 验证原密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误", ErrorCode.OLD_PASSWORD_ERROR);
        }
        
        // 检查新密码不能与原密码相同
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同", ErrorCode.PASSWORD_SAME_AS_OLD);
        }
        
        // 更新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        AuthDateTimeUtil.setUpdateTime(user);
        userRepository.save(user);
        
        logger.info("用户修改密码成功 - 用户ID: {}, 用户名: {}", user.getId(), user.getUsername());
    }
    
    /**
     * 管理员重置用户密码（不需要原密码）
     * @param userId 用户ID
     * @param newPassword 新密码
     */
    @Override
    @Transactional
    @CacheEvict(value = "user", key = "#userId")
    public void resetPassword(Long userId, String newPassword) {
        Optional<User> optional = userRepository.findById(userId);
        if (optional.isEmpty()) {
            throw new BusinessException("用户不存在", ErrorCode.USER_NOT_FOUND);
        }
        
        User user = optional.get();
        
        // 更新密码（管理员重置不需要验证原密码）
        user.setPassword(passwordEncoder.encode(newPassword));
        AuthDateTimeUtil.setUpdateTime(user);
        userRepository.save(user);
        
        logger.info("管理员重置用户密码成功 - 用户ID: {}, 用户名: {}", user.getId(), user.getUsername());
    }
    
    /**
     * 更新用户角色
     * 注意：超级管理员（username为"admin"或id为1）的角色不能被修改
     * @param userId 用户ID
     * @param role 新角色：1-管理员，2-普通用户
     */
    @Override
    @Transactional
    @CacheEvict(value = "user", key = "#userId")
    public void updateUserRole(Long userId, Integer role) {
        Optional<User> optional = userRepository.findById(userId);
        if (optional.isEmpty()) {
            throw new BusinessException("用户不存在", ErrorCode.USER_NOT_FOUND);
        }
        
        User user = optional.get();
        
        // 检查是否是超级管理员
        if (isSuperAdmin(user)) {
            throw new BusinessException("超级管理员的角色不能被修改", ErrorCode.FORBIDDEN);
        }
        
        // 验证角色值
        if (role == null || (role != 1 && role != 2)) {
            throw new BusinessException("无效的角色值", ErrorCode.BAD_REQUEST);
        }
        
        // 更新角色
        user.setRole(role);
        AuthDateTimeUtil.setUpdateTime(user);
        userRepository.save(user);
        
        logger.info("更新用户角色成功 - 用户ID: {}, 用户名: {}, 新角色: {}", 
                user.getId(), user.getUsername(), role);
    }
    
    /**
     * 判断是否是超级管理员
     * 超级管理员：username为"admin"或id为1的用户
     */
    private boolean isSuperAdmin(User user) {
        return "admin".equals(user.getUsername()) || (user.getId() != null && user.getId() == 1L);
    }
    
}

