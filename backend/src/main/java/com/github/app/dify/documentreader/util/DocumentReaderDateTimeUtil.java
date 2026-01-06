package com.github.app.dify.documentreader.util;

import com.github.app.dify.documentreader.domain.DocumentGuide;
import com.github.app.dify.documentreader.domain.DocumentMindMap;
import com.github.app.dify.documentreader.domain.DocumentNotes;
import com.github.app.dify.documentreader.domain.DocumentReader;
import com.github.app.dify.documentreader.domain.DocumentTranslation;

import java.util.Date;

/**
 * 文档解读日期时间工具类
 * 提供统一的日期时间处理方法
 */
public class DocumentReaderDateTimeUtil {
    
    /**
     * 获取当前时间
     * 
     * @return 当前时间
     */
    public static Date now() {
        return new Date();
    }
    
    /**
     * 设置文档的创建时间和更新时间
     * 适用于新建文档
     * 
     * @param document 文档实体
     */
    public static void setCreateAndUpdateTime(DocumentReader document) {
        Date now = now();
        document.setCreateTime(now);
        document.setUpdateTime(now);
    }
    
    /**
     * 设置文档的更新时间
     * 适用于更新文档
     * 
     * @param document 文档实体
     */
    public static void setUpdateTime(DocumentReader document) {
        document.setUpdateTime(now());
    }
    
    /**
     * 设置文档导读的创建时间和更新时间
     * 
     * @param guide 文档导读实体
     */
    public static void setCreateAndUpdateTime(DocumentGuide guide) {
        Date now = now();
        guide.setCreateTime(now);
        guide.setUpdateTime(now);
    }
    
    /**
     * 设置文档导读的更新时间
     * 
     * @param guide 文档导读实体
     */
    public static void setUpdateTime(DocumentGuide guide) {
        guide.setUpdateTime(now());
    }
    
    /**
     * 设置文档翻译的创建时间和更新时间
     * 
     * @param translation 文档翻译实体
     */
    public static void setCreateAndUpdateTime(DocumentTranslation translation) {
        Date now = now();
        translation.setCreateTime(now);
        translation.setUpdateTime(now);
    }
    
    /**
     * 设置文档翻译的更新时间
     * 
     * @param translation 文档翻译实体
     */
    public static void setUpdateTime(DocumentTranslation translation) {
        translation.setUpdateTime(now());
    }
    
    /**
     * 设置思维导图的创建时间和更新时间
     * 
     * @param mindMap 思维导图实体
     */
    public static void setCreateAndUpdateTime(DocumentMindMap mindMap) {
        Date now = now();
        mindMap.setCreateTime(now);
        mindMap.setUpdateTime(now);
    }
    
    /**
     * 设置思维导图的更新时间
     * 
     * @param mindMap 思维导图实体
     */
    public static void setUpdateTime(DocumentMindMap mindMap) {
        mindMap.setUpdateTime(now());
    }
    
    /**
     * 设置文档笔记的创建时间和更新时间
     * 
     * @param notes 文档笔记实体
     */
    public static void setCreateAndUpdateTime(DocumentNotes notes) {
        Date now = now();
        notes.setCreateTime(now);
        notes.setUpdateTime(now);
    }
    
    /**
     * 设置文档笔记的更新时间
     * 
     * @param notes 文档笔记实体
     */
    public static void setUpdateTime(DocumentNotes notes) {
        notes.setUpdateTime(now());
    }
}

