package com.github.app.dify.documentreader.util;

import com.github.app.dify.documentreader.domain.DocumentReader;
import com.github.app.dify.documentreader.resp.DocumentReaderResp;
import org.springframework.beans.BeanUtils;

/**
 * 文档解读实体转换工具类
 * 提供文档解读相关实体的转换方法
 */
public class DocumentReaderConverterUtil {
    
    /**
     * 将 DocumentReader 转换为 DocumentReaderResp
     * 
     * @param document 文档实体
     * @return 文档响应对象
     */
    public static DocumentReaderResp convertToResp(DocumentReader document) {
        if (document == null) {
            return null;
        }
        
        DocumentReaderResp resp = new DocumentReaderResp();
        BeanUtils.copyProperties(document, resp);
        resp.setUploadTime(document.getCreateTime());
        return resp;
    }
}

