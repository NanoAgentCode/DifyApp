package com.github.app.dify.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;
/**
 * 用户应用可见性表
 * @TableName USER_APP_VISIBILITY
 */
@Entity
@Table(name = "USER_APP_VISIBILITY", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "app_id"})
})
public class UserAppVisibility implements Serializable {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    /**
     * 应用ID
     */
    @Column(name = "app_id", nullable = false)
    private Long appId;
    
    /**
     * 是否可见：true-可见，false-不可见
     */
    @Column(name = "visible", nullable = false)
    private Boolean visible;
    
    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;
    
    /**
     * 更新时间
     */
    @Column(name = "update_time")
    private Date updateTime;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}