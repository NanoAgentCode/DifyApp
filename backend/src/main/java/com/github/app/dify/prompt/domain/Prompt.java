package com.github.app.dify.prompt.domain;

import com.github.app.dify.common.domain.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

/**
 * 提示词表（PROMPT）
 */
@Entity
@Table(name = "PROMPT")
public class Prompt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "提示词编号")
    private Long id;

    @Size(max = 200)
    @Schema(description = "提示词标题")
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Schema(description = "提示词正文")
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
