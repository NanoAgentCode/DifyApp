package com.github.app.dify.appsystemdata.repository;

import com.github.app.dify.appsystemdata.domain.DrawIODiagram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * DrawIO 图表 Repository
 */
@Repository
public interface DrawIODiagramRepository extends JpaRepository<DrawIODiagram, Long> {
    
    /**
     * 根据用户ID查询未删除的图表列表
     */
    @Query("SELECT d FROM DrawIODiagram d WHERE d.userId = :userId AND (d.deleted IS NULL OR d.deleted = 0) ORDER BY d.createTime DESC")
    List<DrawIODiagram> findByUserIdAndNotDeleted(@Param("userId") Long userId);
    
    /**
     * 根据ID和用户ID查询图表
     */
    @Query("SELECT d FROM DrawIODiagram d WHERE d.id = :id AND d.userId = :userId AND (d.deleted IS NULL OR d.deleted = 0)")
    Optional<DrawIODiagram> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}

