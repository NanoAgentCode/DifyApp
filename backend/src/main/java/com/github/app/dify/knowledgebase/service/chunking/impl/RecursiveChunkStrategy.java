package com.github.app.dify.knowledgebase.service.chunking.impl;

import com.github.app.dify.knowledgebase.service.chunking.ChunkConfig;
import com.github.app.dify.knowledgebase.service.chunking.ChunkStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归分块策略
 * 多层级分块：先按段落，再按句子，最后按字符
 */
@Component
public class RecursiveChunkStrategy implements ChunkStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(RecursiveChunkStrategy.class);
    
    @Autowired
    private ParagraphChunkStrategy paragraphStrategy;
    
    @Autowired
    private SentenceChunkStrategy sentenceStrategy;
    
    @Autowired
    private FixedSizeChunkStrategy fixedSizeStrategy;
    
    @Override
    public String getName() {
        return "recursive";
    }
    
    @Override
    public boolean supports(String fileType, String contentType) {
        // 适用于复杂文档
        return true;
    }
    
    @Override
    public List<ChunkResult> chunk(String text, ChunkConfig config) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        int chunkSize = config.getChunkSize();
        
        // 第一层：尝试按段落分块
        List<ChunkResult> paragraphChunks = paragraphStrategy.chunk(text, config);
        
        List<ChunkResult> finalChunks = new ArrayList<>();
        
        for (ChunkResult paraChunk : paragraphChunks) {
            String paraContent = paraChunk.getContent();
            
            // 如果段落大小合适，直接使用
            if (paraContent.length() <= chunkSize) {
                finalChunks.add(paraChunk);
            } else {
                // 第二层：按句子分块
                List<ChunkResult> sentenceChunks = sentenceStrategy.chunk(paraContent, config);
                
                for (ChunkResult sentChunk : sentenceChunks) {
                    String sentContent = sentChunk.getContent();
                    
                    // 如果句子大小合适，直接使用
                    if (sentContent.length() <= chunkSize) {
                        // 调整索引
                        sentChunk.setStartIndex(paraChunk.getStartIndex() + 
                                paraContent.indexOf(sentContent));
                        sentChunk.setEndIndex(sentChunk.getStartIndex() + sentContent.length());
                        finalChunks.add(sentChunk);
                    } else {
                        // 第三层：按固定大小分块
                        List<ChunkResult> fixedChunks = fixedSizeStrategy.chunk(sentContent, config);
                        
                        for (ChunkResult fixedChunk : fixedChunks) {
                            // 调整索引
                            int relativeStart = sentContent.indexOf(fixedChunk.getContent());
                            fixedChunk.setStartIndex(paraChunk.getStartIndex() + 
                                    paraContent.indexOf(sentContent) + relativeStart);
                            fixedChunk.setEndIndex(fixedChunk.getStartIndex() + 
                                    fixedChunk.getContent().length());
                            finalChunks.add(fixedChunk);
                        }
                    }
                }
            }
        }
        
        // 重新编号
        for (int i = 0; i < finalChunks.size(); i++) {
            finalChunks.get(i).setChunkIndex(i);
        }
        
        logger.debug("递归分块完成 - 最终chunk数量: {}", finalChunks.size());
        
        return finalChunks;
    }
}
