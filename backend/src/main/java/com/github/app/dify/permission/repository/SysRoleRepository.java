package com.github.app.dify.permission.repository;

import com.github.app.dify.permission.domain.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysRoleRepository extends JpaRepository<SysRole, Long> {
    Optional<SysRole> findByRoleCode(String roleCode);

    boolean existsByRoleCode(String roleCode);

    @Query("SELECT r FROM SysRole r WHERE (r.deleted IS NULL OR r.deleted = 0) ORDER BY r.sortOrder ASC, r.id ASC")
    List<SysRole> findActiveRows();
}
