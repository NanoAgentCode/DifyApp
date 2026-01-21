package com.github.app.dify.knowledgebase.service.impl;

import com.github.app.dify.knowledgebase.service.chunking.*;
import com.github.app.dify.system.config.RagConfig;
import com.github.app.dify.knowledgebase.service.ChunkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
/**
 * 文档分块服务
 * 集成新的分块策略系统，支持根据文件类型自动选择分块方式
 */
@Service
public class ChunkServiceImpl implements ChunkService {
    
    private static final Logger logger = LoggerFactory.getLogger(ChunkServiceImpl.class);
    
    @Autowired
    private RagConfig ragConfig;
    
    @Autowired(required = false)
    private ChunkStrategySelector strategySelector;
    
    @Autowired(required = false)
    private ContentAnalyzer contentAnalyzer;
    
    @Autowired(required = false)
    private MixedContentChunker mixedContentChunker;
    
    /**
     * 文档分块
     */
    @Override
    public List<Chunk> chunkText(String text) {
        return chunkText(text, ragConfig.getChunkSize(), ragConfig.getChunkOverlap());
    }
    
    /**
     * 文档分块（自定义参数）
     * 如果新策略系统可用，使用新策略；否则使用原有实现保持向后兼容
     */
    @Override
    public List<Chunk> chunkText(String text, int chunkSize, int chunkOverlap) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        // 如果新策略系统可用，使用新策略
        if (strategySelector != null) {
            return chunkTextWithNewStrategy(text, chunkSize, chunkOverlap, null);
        }
        
        // 否则使用原有实现（向后兼容）
        return chunkTextLegacy(text, chunkSize, chunkOverlap);
    }
    
    /**
     * 使用新策略系统进行分块
     */
    private List<Chunk> chunkTextWithNewStrategy(String text, int chunkSize, int chunkOverlap, String fileType) {
        // 选择分块策略
        List<ChunkStrategy> strategies = strategySelector.selectStrategy(fileType, text);
        
        // 创建分块配置
        ChunkConfig config = new ChunkConfig(chunkSize, chunkOverlap);
        
        // 如果选择了多个策略（混合内容），使用混合分块器
        if (strategies.size() > 1 && mixedContentChunker != null && contentAnalyzer != null) {
            logger.debug("检测到混合内容，使用混合分块策略");
            ContentStructure structure = contentAnalyzer.analyzeText(text, fileType);
            List<ChunkStrategy.ChunkResult> chunkResults = mixedContentChunker.chunk(structure, config);
            return convertToChunks(chunkResults);
        }
        
        // 使用单个策略
        ChunkStrategy strategy = strategies.get(0);
        logger.debug("使用分块策略: {}", strategy.getName());
        List<ChunkStrategy.ChunkResult> chunkResults = strategy.chunk(text, config);
        return convertToChunks(chunkResults);
    }
    
    /**
     * 将ChunkResult转换为Chunk
     */
    private List<Chunk> convertToChunks(List<ChunkStrategy.ChunkResult> chunkResults) {
        List<Chunk> chunks = new ArrayList<>();
        for (ChunkStrategy.ChunkResult result : chunkResults) {
            Chunk chunk = new Chunk();
            chunk.setContent(result.getContent());
            chunk.setChunkIndex(result.getChunkIndex());
            chunk.setStartIndex(result.getStartIndex());
            chunk.setEndIndex(result.getEndIndex());
            chunks.add(chunk);
        }
        return chunks;
    }
    
    /**
     * 原有实现（向后兼容）
     */
    private List<Chunk> chunkTextLegacy(String text, int chunkSize, int chunkOverlap) {
        List<Chunk> chunks = new ArrayList<>();
        
        // 按字符数分块（简单实现，可以改进为按句子或段落分块）
        int textLength = text.length();
        int start = 0;
        int chunkIndex = 0;
        
        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);
            String chunkText = text.substring(start, end);
            
            // 尝试在单词边界处截断（避免截断单词）
            if (end < textLength && chunkText.length() == chunkSize) {
                int lastSpace = chunkText.lastIndexOf(' ');
                int lastNewline = chunkText.lastIndexOf('\n');
                int lastPunctuation = Math.max(
                        chunkText.lastIndexOf('。'),
                        Math.max(chunkText.lastIndexOf('！'),
                                chunkText.lastIndexOf('？')));
                
                int bestBreak = Math.max(Math.max(lastSpace, lastNewline), lastPunctuation);
                if (bestBreak > chunkSize * 0.5) { // 至少保留50%的内容
                    chunkText = chunkText.substring(0, bestBreak + 1);
                    end = start + bestBreak + 1;
                }
            }
            
            if (!chunkText.trim().isEmpty()) {
                Chunk chunk = new Chunk();
                chunk.setContent(chunkText.trim());
                chunk.setChunkIndex(chunkIndex);
                chunk.setStartIndex(start);
                chunk.setEndIndex(end);
                chunks.add(chunk);
                chunkIndex++;
            }
            
            // 移动到下一个chunk的起始位置（考虑重叠）
            start = end - chunkOverlap;
            if (start <= 0) {
                start = end;
            }
        }
        
        logger.debug("文档分块完成（原有实现） - 总长度: {}, chunk数量: {}, chunk大小: {}, 重叠: {}", 
                textLength, chunks.size(), chunkSize, chunkOverlap);
        
        return chunks;
    }
}