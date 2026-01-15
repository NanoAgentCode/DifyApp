package com.github.app.dify.documentreader.util;

import com.github.app.dify.common.util.EntityLifecycleUtil;
import com.github.app.dify.documentreader.domain.DocumentReader;
import org.springframework.data.repository.CrudRepository;

/**
 * 文档解读软删除工具类
 * 提供统一的软删除操作方法
 */
public class DocumentReaderSoftDeleteUtil {
    
    /**
     * 软删除文档（设置 deleted = 1 和 updateTime）
     * 
     * @param document 文档实体
     * @param repository 文档仓库
     */
    public static void softDelete(DocumentReader document, CrudRepository<DocumentReader, Long> repository) {
        EntityLifecycleUtil.softDelete(document, repository);
    }
    
    /**
     * 恢复软删除的文档（设置 deleted = 0 和 updateTime）
     * 
     * @param document 文档实体
     * @param repository 文档仓库
     */
    public static void restore(DocumentReader document, CrudRepository<DocumentReader, Long> repository) {
        EntityLifecycleUtil.restore(document, repository);
    }
    
    /**
     * 检查文档是否已删除
     * 
     * @param document 文档实体
     * @return true 如果已删除，false 如果未删除
     */
    public static boolean isDeleted(DocumentReader document) {
        return EntityLifecycleUtil.isDeleted(document);
    }
}
