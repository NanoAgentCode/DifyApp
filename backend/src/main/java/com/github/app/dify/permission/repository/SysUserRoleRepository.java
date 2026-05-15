package com.github.app.dify.permission.repository;

import com.github.app.dify.permission.domain.SysUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SysUserRoleRepository extends JpaRepository<SysUserRole, Long> {
    List<SysUserRole> findByUserId(Long userId);

    List<SysUserRole> findByUserIdIn(Collection<Long> userIds);

    @Modifying
    @Query("DELETE FROM SysUserRole ur WHERE ur.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
