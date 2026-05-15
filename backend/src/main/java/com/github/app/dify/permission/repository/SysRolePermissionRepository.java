package com.github.app.dify.permission.repository;

import com.github.app.dify.permission.domain.SysRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SysRolePermissionRepository extends JpaRepository<SysRolePermission, Long> {
    List<SysRolePermission> findByRoleId(Long roleId);

    List<SysRolePermission> findByRoleIdIn(Collection<Long> roleIds);

    @Modifying
    @Query("DELETE FROM SysRolePermission rp WHERE rp.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);
}
