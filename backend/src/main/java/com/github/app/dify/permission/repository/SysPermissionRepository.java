package com.github.app.dify.permission.repository;

import com.github.app.dify.permission.domain.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {
    Optional<SysPermission> findByPermissionCode(String permissionCode);

    boolean existsByPermissionCode(String permissionCode);

    List<SysPermission> findByPermissionCodeIn(Collection<String> permissionCodes);

    @Query("SELECT p FROM SysPermission p WHERE (p.deleted IS NULL OR p.deleted = 0) ORDER BY p.clientType ASC, p.sortOrder ASC, p.id ASC")
    List<SysPermission> findActiveRows();
}
