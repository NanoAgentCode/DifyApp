package com.github.app.dify.permission.service.impl;

import com.github.app.dify.auth.domain.User;
import com.github.app.dify.auth.repository.UserRepository;
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.permission.domain.SysPermission;
import com.github.app.dify.permission.domain.SysRole;
import com.github.app.dify.permission.domain.SysRolePermission;
import com.github.app.dify.permission.domain.SysUserRole;
import com.github.app.dify.permission.repository.SysPermissionRepository;
import com.github.app.dify.permission.repository.SysRolePermissionRepository;
import com.github.app.dify.permission.repository.SysRoleRepository;
import com.github.app.dify.permission.repository.SysUserRoleRepository;
import com.github.app.dify.permission.req.RoleSaveReq;
import com.github.app.dify.permission.resp.PermissionResp;
import com.github.app.dify.permission.resp.RoleResp;
import com.github.app.dify.permission.service.RbacService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RbacServiceImpl implements RbacService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ADMIN = "ADMIN";
    private static final String USER = "USER";

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysPermissionRepository permissionRepository;

    @Autowired
    private SysUserRoleRepository userRoleRepository;

    @Autowired
    private SysRolePermissionRepository rolePermissionRepository;

    @Autowired
    private UserRepository userRepository;

    @PostConstruct
    @Transactional
    public void initializeDefaults() {
        List<SysPermission> permissions = seedPermissions();
        SysRole superAdmin = seedRole(SUPER_ADMIN, "超级管理员", "系统内置超级管理员", 1, 1, true);
        SysRole admin = seedRole(ADMIN, "管理员", "系统内置管理员", 1, 2, true);
        SysRole user = seedRole(USER, "普通用户", "系统内置普通用户", 1, 3, true);

        grantPermissions(superAdmin, permissions);
        grantPermissions(admin, permissions.stream()
                .filter(p -> p.getPermissionCode().startsWith("admin.") || p.getPermissionCode().startsWith("user."))
                .collect(Collectors.toList()));
        grantPermissions(user, permissions.stream()
                .filter(p -> p.getPermissionCode().startsWith("user."))
                .collect(Collectors.toList()));
        migrateLegacyUsers(superAdmin, admin, user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResp> listRoles() {
        Map<Long, List<String>> permissionsByRole = loadPermissionCodesByRole();
        return roleRepository.findActiveRows().stream()
                .map(role -> toRoleResp(role, permissionsByRole.getOrDefault(role.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RoleResp createRole(RoleSaveReq req) {
        String code = normalizeRoleCode(req.getRoleCode());
        if (roleRepository.existsByRoleCode(code)) {
            throw new BusinessException("角色编码已存在", ErrorCode.BAD_REQUEST);
        }
        SysRole role = new SysRole();
        role.setRoleCode(code);
        role.setRoleName(req.getRoleName());
        role.setDescription(req.getDescription());
        role.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        role.setSortOrder(req.getSortOrder() == null ? 100 : req.getSortOrder());
        role.setSystemRole(false);
        touchCreate(role);
        return toRoleResp(roleRepository.save(role), Collections.emptyList());
    }

    @Override
    @Transactional
    public RoleResp updateRole(Long roleId, RoleSaveReq req) {
        SysRole role = getRole(roleId);
        if (Boolean.TRUE.equals(role.getSystemRole())) {
            role.setRoleName(req.getRoleName());
            role.setDescription(req.getDescription());
            role.setStatus(req.getStatus() == null ? role.getStatus() : req.getStatus());
            role.setSortOrder(req.getSortOrder() == null ? role.getSortOrder() : req.getSortOrder());
        } else {
            String code = normalizeRoleCode(req.getRoleCode());
            roleRepository.findByRoleCode(code).ifPresent(existing -> {
                if (!existing.getId().equals(roleId)) {
                    throw new BusinessException("角色编码已存在", ErrorCode.BAD_REQUEST);
                }
            });
            role.setRoleCode(code);
            role.setRoleName(req.getRoleName());
            role.setDescription(req.getDescription());
            role.setStatus(req.getStatus() == null ? 1 : req.getStatus());
            role.setSortOrder(req.getSortOrder() == null ? 100 : req.getSortOrder());
        }
        touchUpdate(role);
        return toRoleResp(roleRepository.save(role), getPermissionCodesForRole(role.getId()));
    }

    @Override
    @Transactional
    public void deleteRole(Long roleId) {
        SysRole role = getRole(roleId);
        if (Boolean.TRUE.equals(role.getSystemRole())) {
            throw new BusinessException("系统内置角色不能删除", ErrorCode.FORBIDDEN);
        }
        role.setDeleted(1);
        touchUpdate(role);
        roleRepository.save(role);
        rolePermissionRepository.deleteByRoleId(roleId);
        userRoleRepository.findAll().stream()
                .filter(ur -> roleId.equals(ur.getRoleId()))
                .forEach(userRoleRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResp> listPermissions() {
        return permissionRepository.findActiveRows().stream().map(this::toPermissionResp).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getUserPermissionCodes(Long userId) {
        if (isSuperAdmin(userId)) {
            return permissionRepository.findActiveRows().stream()
                    .map(SysPermission::getPermissionCode)
                    .collect(Collectors.toList());
        }
        List<Long> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            return legacyPermissionCodes(userId);
        }
        List<Long> permissionIds = rolePermissionRepository.findByRoleIdIn(roleIds).stream()
                .map(SysRolePermission::getPermissionId)
                .distinct()
                .collect(Collectors.toList());
        if (permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return permissionRepository.findAllById(permissionIds).stream()
                .filter(p -> p.getStatus() == null || p.getStatus() == 1)
                .filter(p -> p.getDeleted() == null || p.getDeleted() == 0)
                .sorted(Comparator.comparing(SysPermission::getClientType).thenComparing(SysPermission::getSortOrder))
                .map(SysPermission::getPermissionCode)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResp> getUserRoles(Long userId) {
        List<Long> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(SysUserRole::getRoleId)
                .collect(Collectors.toList());
        if (roleIds.isEmpty()) {
            roleIds = legacyRoleIds(userId);
        }
        Map<Long, List<String>> permissionsByRole = loadPermissionCodesByRole();
        return roleRepository.findAllById(roleIds).stream()
                .filter(r -> r.getDeleted() == null || r.getDeleted() == 0)
                .map(role -> toRoleResp(role, permissionsByRole.getOrDefault(role.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateUserRoles(Long userId, List<Long> roleIds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在", ErrorCode.USER_NOT_FOUND));
        if (isSuperAdminUser(user)) {
            throw new BusinessException("超级管理员角色不能被修改", ErrorCode.FORBIDDEN);
        }
        List<Long> safeRoleIds = roleIds == null ? Collections.emptyList() : roleIds.stream().distinct().collect(Collectors.toList());
        if (safeRoleIds.isEmpty()) {
            throw new BusinessException("用户至少需要一个角色", ErrorCode.BAD_REQUEST);
        }
        List<SysRole> roles = roleRepository.findAllById(safeRoleIds).stream()
                .filter(r -> r.getDeleted() == null || r.getDeleted() == 0)
                .collect(Collectors.toList());
        if (roles.size() != safeRoleIds.size()) {
            throw new BusinessException("角色不存在或已删除", ErrorCode.BAD_REQUEST);
        }
        userRoleRepository.deleteByUserId(userId);
        Date now = new Date();
        for (Long roleId : safeRoleIds) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setCreateTime(now);
            userRoleRepository.save(userRole);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getRolePermissionIds(Long roleId) {
        return rolePermissionRepository.findByRoleId(roleId).stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateRolePermissions(Long roleId, List<Long> permissionIds) {
        SysRole role = getRole(roleId);
        if (SUPER_ADMIN.equals(role.getRoleCode())) {
            throw new BusinessException("超级管理员默认拥有全部权限，无需修改", ErrorCode.FORBIDDEN);
        }
        List<Long> safePermissionIds = permissionIds == null ? Collections.emptyList() : permissionIds.stream().distinct().collect(Collectors.toList());
        if (!safePermissionIds.isEmpty() && permissionRepository.findAllById(safePermissionIds).size() != safePermissionIds.size()) {
            throw new BusinessException("权限不存在", ErrorCode.BAD_REQUEST);
        }
        rolePermissionRepository.deleteByRoleId(roleId);
        Date now = new Date();
        for (Long permissionId : safePermissionIds) {
            SysRolePermission rolePermission = new SysRolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permissionId);
            rolePermission.setCreateTime(now);
            rolePermissionRepository.save(rolePermission);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, String permissionCode) {
        return getUserPermissionCodes(userId).contains(permissionCode);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSuperAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findById(userId).map(this::isSuperAdminUser).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> getSuperAdminRoleIds() {
        return roleRepository.findByRoleCode(SUPER_ADMIN)
                .map(role -> Collections.singleton(role.getId()))
                .orElse(Collections.emptySet());
    }

    private List<SysPermission> seedPermissions() {
        List<PermissionSeed> seeds = Arrays.asList(
                new PermissionSeed("admin.chat", "管理端-智能问答", "admin", "/admin/chat", "ChatLineRound", 10),
                new PermissionSeed("admin.apps", "管理端-应用管理", "admin", "/admin/apps", "Grid", 20),
                new PermissionSeed("admin.models", "管理端-组件管理", "admin", "/admin/models", "Setting", 30),
                new PermissionSeed("admin.users", "管理端-用户管理", "admin", "/admin/users", "User", 40),
                new PermissionSeed("admin.roles", "管理端-角色管理", "admin", "/admin/roles", "Lock", 45),
                new PermissionSeed("admin.system_config", "管理端-系统配置", "admin", "/admin/system-config", "Tools", 50),
                new PermissionSeed("admin.skills", "管理端-Skills管理", "admin", "/admin/skills", "Cpu", 60),
                new PermissionSeed("admin.statistics", "管理端-数据统计", "admin", "/admin/statistics", "DataAnalysis", 70),
                new PermissionSeed("admin.data_analysis", "管理端-数据分析", "admin", "/admin/data-analysis", "Share", 80),
                new PermissionSeed("admin.user_logs", "管理端-行为日志", "admin", "/admin/user-action-logs", "Document", 90),
                new PermissionSeed("admin.observability", "管理端-日志监控", "admin", "/admin/observability", "Monitor", 100),
                new PermissionSeed("admin.memos", "管理端-备忘录", "admin", "/admin/memos", "Bell", 110),
                new PermissionSeed("admin.knowledge_base", "管理端-知识管理", "admin", "/admin/knowledge-base", "Collection", 120),
                new PermissionSeed("admin.documents", "管理端-文档管理", "admin", "/admin/knowledge-base/:kbId/documents", "Files", 130),
                new PermissionSeed("admin.chat_history", "管理端-会话历史", "admin", "/admin/chat-history", "Clock", 140),
                new PermissionSeed("admin.document_reader", "管理端-文档解读", "admin", "/admin/document-reader", "Reading", 150),
                new PermissionSeed("admin.ai_drawio", "管理端-智能框图", "admin", "/admin/ai-drawio", "EditPen", 160),
                new PermissionSeed("user.chat", "用户端-智能问答", "user", "/user/chat", "ChatLineRound", 10),
                new PermissionSeed("user.apps", "用户端-智能应用", "user", "/user/apps", "Grid", 20),
                new PermissionSeed("user.kb_qa", "用户端-知识检索", "user", "/user/kb-qa", "Search", 30),
                new PermissionSeed("user.knowledge_base", "用户端-知识管理", "user", "/user/knowledge-base", "Collection", 40),
                new PermissionSeed("user.documents", "用户端-文档管理", "user", "/user/knowledge-base/:kbId/documents", "Files", 50),
                new PermissionSeed("user.chat_history", "用户端-会话历史", "user", "/user/chat-history", "Clock", 60),
                new PermissionSeed("user.ai_drawio", "用户端-智能框图", "user", "/user/ai-drawio", "EditPen", 70),
                new PermissionSeed("user.document_reader", "用户端-文档解读", "user", "/user/document-reader", "Reading", 80),
                new PermissionSeed("user.memos", "用户端-备忘录", "user", "/user/memos", "Bell", 90)
        );
        List<SysPermission> permissions = new ArrayList<>();
        for (PermissionSeed seed : seeds) {
            SysPermission permission = permissionRepository.findByPermissionCode(seed.code).orElseGet(SysPermission::new);
            permission.setPermissionCode(seed.code);
            permission.setPermissionName(seed.name);
            permission.setClientType(seed.clientType);
            permission.setRoutePath(seed.routePath);
            permission.setIcon(seed.icon);
            permission.setSortOrder(seed.sortOrder);
            permission.setStatus(1);
            if (permission.getId() == null) {
                touchCreate(permission);
            } else {
                touchUpdate(permission);
            }
            permissions.add(permissionRepository.save(permission));
        }
        return permissions;
    }

    private SysRole seedRole(String code, String name, String description, Integer status, Integer sortOrder, Boolean systemRole) {
        SysRole role = roleRepository.findByRoleCode(code).orElseGet(SysRole::new);
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setDescription(description);
        role.setStatus(status);
        role.setSortOrder(sortOrder);
        role.setSystemRole(systemRole);
        if (role.getId() == null) {
            touchCreate(role);
        } else {
            touchUpdate(role);
        }
        return roleRepository.save(role);
    }

    private void grantPermissions(SysRole role, List<SysPermission> permissions) {
        Set<Long> existingPermissionIds = rolePermissionRepository.findByRoleId(role.getId()).stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toSet());
        Date now = new Date();
        for (SysPermission permission : permissions) {
            if (!existingPermissionIds.contains(permission.getId())) {
                SysRolePermission rolePermission = new SysRolePermission();
                rolePermission.setRoleId(role.getId());
                rolePermission.setPermissionId(permission.getId());
                rolePermission.setCreateTime(now);
                rolePermissionRepository.save(rolePermission);
            }
        }
    }

    private void migrateLegacyUsers(SysRole superAdmin, SysRole admin, SysRole userRole) {
        Date now = new Date();
        for (User user : userRepository.findByDeletedIsNullOrDeleted()) {
            if (!userRoleRepository.findByUserId(user.getId()).isEmpty()) {
                continue;
            }
            Long roleId = isSuperAdminUser(user) ? superAdmin.getId() : (user.getRole() != null && user.getRole() == 1 ? admin.getId() : userRole.getId());
            SysUserRole relation = new SysUserRole();
            relation.setUserId(user.getId());
            relation.setRoleId(roleId);
            relation.setCreateTime(now);
            userRoleRepository.save(relation);
        }
    }

    private Map<Long, List<String>> loadPermissionCodesByRole() {
        List<SysPermission> permissions = permissionRepository.findActiveRows();
        Map<Long, SysPermission> permissionMap = permissions.stream().collect(Collectors.toMap(SysPermission::getId, Function.identity()));
        return rolePermissionRepository.findAll().stream()
                .filter(rp -> permissionMap.containsKey(rp.getPermissionId()))
                .collect(Collectors.groupingBy(SysRolePermission::getRoleId,
                        Collectors.mapping(rp -> permissionMap.get(rp.getPermissionId()).getPermissionCode(), Collectors.toList())));
    }

    private List<String> getPermissionCodesForRole(Long roleId) {
        List<Long> permissionIds = rolePermissionRepository.findByRoleId(roleId).stream()
                .map(SysRolePermission::getPermissionId)
                .collect(Collectors.toList());
        if (permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return permissionRepository.findAllById(permissionIds).stream()
                .map(SysPermission::getPermissionCode)
                .collect(Collectors.toList());
    }

    private List<String> legacyPermissionCodes(Long userId) {
        return userRepository.findById(userId).map(user -> {
            boolean admin = user.getRole() != null && user.getRole() == 1;
            return permissionRepository.findActiveRows().stream()
                    .filter(p -> admin || p.getPermissionCode().startsWith("user."))
                    .map(SysPermission::getPermissionCode)
                    .collect(Collectors.toList());
        }).orElse(Collections.emptyList());
    }

    private List<Long> legacyRoleIds(Long userId) {
        Optional<User> optional = userRepository.findById(userId);
        if (optional.isEmpty()) {
            return Collections.emptyList();
        }
        User user = optional.get();
        String code = isSuperAdminUser(user) ? SUPER_ADMIN : (user.getRole() != null && user.getRole() == 1 ? ADMIN : USER);
        return roleRepository.findByRoleCode(code).map(role -> Collections.singletonList(role.getId())).orElse(Collections.emptyList());
    }

    private boolean isSuperAdminUser(User user) {
        return user != null && ("admin".equals(user.getUsername()) || (user.getId() != null && user.getId() == 1L));
    }

    private SysRole getRole(Long roleId) {
        return roleRepository.findById(roleId)
                .filter(role -> role.getDeleted() == null || role.getDeleted() == 0)
                .orElseThrow(() -> new BusinessException("角色不存在", ErrorCode.BAD_REQUEST));
    }

    private String normalizeRoleCode(String roleCode) {
        return roleCode == null ? null : roleCode.trim().toUpperCase(Locale.ROOT);
    }

    private RoleResp toRoleResp(SysRole role, List<String> permissionCodes) {
        RoleResp resp = new RoleResp();
        resp.setId(role.getId());
        resp.setRoleCode(role.getRoleCode());
        resp.setRoleName(role.getRoleName());
        resp.setDescription(role.getDescription());
        resp.setStatus(role.getStatus());
        resp.setSortOrder(role.getSortOrder());
        resp.setSystemRole(role.getSystemRole());
        resp.setCreateTime(role.getCreateTime());
        resp.setUpdateTime(role.getUpdateTime());
        resp.setPermissionCodes(permissionCodes);
        return resp;
    }

    private PermissionResp toPermissionResp(SysPermission permission) {
        PermissionResp resp = new PermissionResp();
        resp.setId(permission.getId());
        resp.setPermissionCode(permission.getPermissionCode());
        resp.setPermissionName(permission.getPermissionName());
        resp.setClientType(permission.getClientType());
        resp.setRoutePath(permission.getRoutePath());
        resp.setIcon(permission.getIcon());
        resp.setSortOrder(permission.getSortOrder());
        resp.setStatus(permission.getStatus());
        return resp;
    }

    private void touchCreate(SysRole entity) {
        Date now = new Date();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
    }

    private void touchUpdate(SysRole entity) {
        entity.setUpdateTime(new Date());
        if (entity.getDeleted() == null) {
            entity.setDeleted(0);
        }
    }

    private void touchCreate(SysPermission entity) {
        Date now = new Date();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
    }

    private void touchUpdate(SysPermission entity) {
        entity.setUpdateTime(new Date());
        if (entity.getDeleted() == null) {
            entity.setDeleted(0);
        }
    }

    private static class PermissionSeed {
        final String code;
        final String name;
        final String clientType;
        final String routePath;
        final String icon;
        final Integer sortOrder;

        PermissionSeed(String code, String name, String clientType, String routePath, String icon, Integer sortOrder) {
            this.code = code;
            this.name = name;
            this.clientType = clientType;
            this.routePath = routePath;
            this.icon = icon;
            this.sortOrder = sortOrder;
        }
    }
}
