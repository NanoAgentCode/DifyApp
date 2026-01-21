package com.github.app.dify.knowledgebase.service.chunking;

import com.github.app.dify.knowledgebase.service.chunking.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 混合内容分块器
 * 处理包含多种内容类型的文档（如表格+文本）
 */
@Component
public class MixedContentChunker {
    
    private static final Logger logger = LoggerFactory.getLogger(MixedContentChunker.class);
    
    @Autowired
    private TableChunkStrategy tableStrategy;
    
    @Autowired
    private CodeChunkStrategy codeStrategy;
    
    @Autowired
    private HeadingChunkStrategy headingStrategy;
    
    @Autowired
    private ParagraphChunkStrategy paragraphStrategy;
    
    @Autowired
    private FixedSizeChunkStrategy fixedSizeStrategy;
    
    /**
     * 分块混合内容
     * 
     * @param structure 内容结构
     * @param config 分块配置
     * @return 分块结果列表
     */
    public List<ChunkStrategy.ChunkResult> chunk(ContentStructure structure, ChunkConfig config) {
        List<ChunkStrategy.ChunkResult> allChunks = new ArrayList<>();
        
        // 按原始顺序处理每个片段
        List<ContentStructure.ContentSegment> segments = structure.getSegments();
        segments.sort(Comparator.comparingInt(ContentStructure.ContentSegment::getStartIndex));
        
        int globalChunkIndex = 0;
        
        for (ContentStructure.ContentSegment segment : segments) {
            String contentType = segment.getType();
            String content = segment.getContent();
            
            List<ChunkStrategy.ChunkResult> chunks;
            
            // 根据内容类型选择策略
            switch (contentType) {
                case ContentStructure.ContentType.TABLE:
                    chunks = tableStrategy.chunk(content, config);
                    break;
                case ContentStructure.ContentType.CODE:
                    chunks = codeStrategy.chunk(content, config);
                    break;
                case ContentStructure.ContentType.HEADING:
                    chunks = headingStrategy.chunk(content, config);
                    break;
                case ContentStructure.ContentType.TEXT:
                default:
                    chunks = paragraphStrategy.chunk(content, config);
                    break;
            }
            
            // 调整索引和元数据
            for (ChunkStrategy.ChunkResult chunk : chunks) {
                // 调整全局索引（优化：chunk的startIndex和endIndex是相对于segment内容的，需要加上segment的起始位置）
                int relativeStart = chunk.getStartIndex();
                int relativeEnd = chunk.getEndIndex();
                chunk.setStartIndex(segment.getStartIndex() + relativeStart);
                chunk.setEndIndex(segment.getStartIndex() + relativeEnd);
                
                // 设置全局chunk索引
                chunk.setChunkIndex(globalChunkIndex++);
                
                // 保留内容类型
                chunk.setContentType(contentType);
                
                // 合并元数据
                segment.getMetadata().forEach(chunk::addMetadata);
            }
            
            allChunks.addAll(chunks);
        }
        
        logger.debug("混合内容分块完成 - 片段数: {}, 总chunk数: {}", 
                segments.size(), allChunks.size());
        
        return allChunks;
    }
}
