package com.github.app.dify.knowledgebase.util;

import com.github.app.dify.common.resp.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库分页工具类
 * 提供统一的分页查询和响应构建方法
 */
public class KnowledgeBasePageUtil {
    
    /**
     * 创建分页请求对象（默认按创建时间倒序）
     * 
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     * @return Pageable 对象
     */
    public static Pageable createPageable(int page, int pageSize) {
        return PageRequest.of(
                page - 1,  // Spring Data JPA 页码从0开始
                pageSize,
                Sort.by("createTime").descending()
        );
    }
    
    /**
     * 创建分页请求对象（指定排序字段）
     * 
     * @param page 页码（从1开始）
     * @param pageSize 每页大小
     * @param sortField 排序字段
     * @param ascending 是否升序
     * @return Pageable 对象
     */
    public static Pageable createPageable(int page, int pageSize, String sortField, boolean ascending) {
        Sort sort = ascending 
                ? Sort.by(sortField).ascending()
                : Sort.by(sortField).descending();
        return PageRequest.of(page - 1, pageSize, sort);
    }
    
    /**
     * 将 Spring Data Page 转换为 PageResponse
     * 
     * @param page Spring Data Page 对象
     * @param converter 实体转换函数
     * @param <E> 实体类型
     * @param <R> 响应类型
     * @return PageResponse 对象
     */
    public static <E, R> PageResponse<R> toPageResponse(Page<E> page, Function<E, R> converter) {
        PageResponse<R> response = new PageResponse<>();
        
        List<R> content = page.getContent().stream()
                .map(converter)
                .collect(Collectors.toList());
        
        response.setContent(content);
        response.setTotal(page.getTotalElements());
        response.setPage(page.getNumber() + 1);  // 转换为从1开始的页码
        response.setPageSize(page.getSize());
        response.setTotalPages(page.getTotalPages());
        
        return response;
    }
    
    /**
     * 将 Spring Data Page 转换为 PageResponse（直接使用实体对象）
     * 
     * @param page Spring Data Page 对象
     * @param <E> 实体类型
     * @return PageResponse 对象
     */
    public static <E> PageResponse<E> toPageResponse(Page<E> page) {
        return toPageResponse(page, Function.identity());
    }
    
    /**
     * 创建空的分页响应
     * 
     * @param page 页码
     * @param pageSize 每页大小
     * @param <T> 响应类型
     * @return PageResponse 对象
     */
    public static <T> PageResponse<T> emptyPageResponse(int page, int pageSize) {
        PageResponse<T> response = new PageResponse<>();
        response.setContent(List.of());
        response.setTotal(0);
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotalPages(0);
        return response;
    }
}

