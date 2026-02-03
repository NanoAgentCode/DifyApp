package com.github.app.dify.prompt.repository;

import com.github.app.dify.prompt.domain.Prompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromptRepository extends JpaRepository<Prompt, Long> {

    @Query("SELECT p FROM Prompt p WHERE (p.deleted IS NULL OR p.deleted = 0) " +
            "AND (:keyword IS NULL OR :keyword = '' OR LOWER(p.title) LIKE LOWER(CONCAT(CONCAT('%', :keyword), '%')) " +
            "OR LOWER(p.content) LIKE LOWER(CONCAT(CONCAT('%', :keyword), '%'))) " +
            "ORDER BY p.updateTime DESC")
    List<Prompt> findAllActiveByKeyword(@Param("keyword") String keyword);

    @Query("SELECT p FROM Prompt p WHERE p.id = :id AND (p.deleted IS NULL OR p.deleted = 0)")
    Optional<Prompt> findActiveById(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Prompt p SET p.deleted = 1, p.updateTime = CURRENT_TIMESTAMP WHERE p.id = :id AND (p.deleted IS NULL OR p.deleted = 0)")
    int softDeleteById(@Param("id") Long id);
}
