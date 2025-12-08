package com.github.app.dify.resp;

import java.util.List;
/**
 * 分页响应
 */
public class PageResponse<T> {
    private List<T> content;
    private long total;
    private int page;
    private int pageSize;
    private int totalPages;
    
    public PageResponse() {
    }
    
    public PageResponse(List<T> content, long total, int page, int pageSize) {
        this.content = content;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }
    
    public List<T> getContent() {
        return content;
    }
    
    public void setContent(List<T> content) {
        this.content = content;
    }
    
    public long getTotal() {
        return total;
    }
    
    public void setTotal(long total) {
        this.total = total;
    }
    
    public int getPage() {
        return page;
    }
    
    public void setPage(int page) {
        this.page = page;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
    
    public int getTotalPages() {
        return totalPages;
    }
    
    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}