package com.github.app.dify.req;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.Date;

/**
 * 历史查询请求
 */
@ApiModel("历史查询请求")
public class ChatHistoryRequest {
    
    @ApiModelProperty("页码（从1开始）")
    private Integer page = 1;
    
    @ApiModelProperty("每页大小")
    private Integer size = 20;
    
    @ApiModelProperty("关键词（搜索标题）")
    private String keyword;
    
    @ApiModelProperty("对话类型（1-普通聊天，2-知识库问答）")
    private Integer type;
    
    @ApiModelProperty("用户ID（管理员端使用）")
    private Long userId;
    
    @ApiModelProperty("开始时间（管理员端使用）")
    private Date startTime;
    
    @ApiModelProperty("结束时间（管理员端使用）")
    private Date endTime;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }
}

