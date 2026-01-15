package com.github.app.dify.system.domain;

import com.github.app.dify.common.domain.BaseSoftDeleteEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.Length;

/**
 * 提示词表
 * @TableName PROMPT
 */
@Entity
@Table(name = "PROMPT")
public class Prompt extends BaseSoftDeleteEntity implements Serializable {

    /**
     * 提示词编号
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "提示词编号")
    private Long id;
    
    /**
     * 提示词标题
     */
    @NotBlank(message="[提示词标题]不能为空")
    @Size(max= 200,message="编码长度不能超过200")
    @Schema(description = "提示词标题")
    @Length(max= 200,message="编码长度不能超过200")
    @Column(name = "title", columnDefinition = "VARCHAR(200)")
    private String title;
    
    /**
     * 提示词正文
     */
    @NotBlank(message="[提示词正文]不能为空")
    @Schema(description = "提示词正文")
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    
    // Getters and Setters
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
