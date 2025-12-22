package com.github.app.dify.knowledgebase.service.impl;

import com.github.app.dify.knowledgebase.domain.VectorDatabase;
import com.github.app.dify.knowledgebase.repository.VectorDatabaseRepository;
import com.github.app.dify.knowledgebase.service.VectorStoreStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PgVector向量存储策略实现
 * 使用PostgreSQL的pgvector扩展存储向量
 */
@Service
public class PgVectorVectorStoreStrategy implements VectorStoreStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(PgVectorVectorStoreStrategy.class);
    
    @Override
    public String getType() {
        return "pgvector";
    }
    
    @Autowired(required = false)
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    // 为每个知识库缓存数据库连接
    private final Map<Long, Connection> connectionCache = new ConcurrentHashMap<>();
    private final Map<Long, String> lastUrlCache = new ConcurrentHashMap<>();
    private final Map<Long, String> lastUsernameCache = new ConcurrentHashMap<>();
    private final Map<Long, String> lastPasswordCache = new ConcurrentHashMap<>();
    
    /**
     * 获取指定知识库的数据库连接
     */
    private Connection getConnection(Long knowledgeBaseId) {
        // 获取对应的配置
        String currentUrl;
        String currentUsername;
        String currentPassword;
        
        VectorDatabase config = getConfigByType("pgvector");
        if (config != null) {
            currentUrl = config.getUrl();
            // 从extraConfig中解析username和password
            currentUsername = null;
            currentPassword = null;
            if (config.getExtraConfig() != null && !config.getExtraConfig().trim().isEmpty()) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> extraConfig = mapper.readValue(config.getExtraConfig(), Map.class);
                    if (extraConfig.containsKey("username")) {
                        currentUsername = (String) extraConfig.get("username");
                    }
                    if (extraConfig.containsKey("password")) {
                        currentPassword = (String) extraConfig.get("password");
                    }
                } catch (Exception e) {
                    logger.debug("解析extraConfig失败: {}", e.getMessage());
                }
            }
            
            // 如果没有从extraConfig获取到，尝试从apiKey（向后兼容）
            if (currentUsername == null && config.getApiKey() != null && !config.getApiKey().trim().isEmpty()) {
                if (config.getApiKey().contains(":")) {
                    String[] parts = config.getApiKey().split(":", 2);
                    currentUsername = parts[0];
                    currentPassword = parts.length > 1 ? parts[1] : "";
                }
            }
        } else {
            throw new RuntimeException("未找到pgvector配置，请先在向量库管理中配置pgvector");
        }
        
        if (currentUsername == null || currentUsername.trim().isEmpty()) {
            throw new RuntimeException("pgvector配置缺少用户名，请在extraConfig中配置用户名和密码");
        }
        
        // 验证URL格式
        String jdbcUrl = currentUrl.trim();
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            if (jdbcUrl.contains("://")) {
                jdbcUrl = jdbcUrl.replaceFirst("^postgresql://", "jdbc:postgresql://");
            } else {
                jdbcUrl = "jdbc:postgresql://" + jdbcUrl;
            }
        }
        
        // 检查缓存
        String lastUrl = lastUrlCache.get(knowledgeBaseId);
        String lastUsername = lastUsernameCache.get(knowledgeBaseId);
        String lastPassword = lastPasswordCache.get(knowledgeBaseId);
        Connection conn = connectionCache.get(knowledgeBaseId);
        
        boolean needNewConnection = false;
        if (conn == null) {
            needNewConnection = true;
        } else {
            try {
                if (conn.isClosed() ||
                    !jdbcUrl.equals(lastUrl) ||
                    (currentUsername != null ? !currentUsername.equals(lastUsername) : lastUsername != null) ||
                    (currentPassword != null ? !currentPassword.equals(lastPassword) : lastPassword != null)) {
                    needNewConnection = true;
                }
            } catch (SQLException e) {
                logger.warn("检查连接状态失败，创建新连接", e);
                needNewConnection = true;
            }
        }
        
        if (needNewConnection) {
            
            // 关闭旧的连接
            if (conn != null) {
                try {
                    if (!conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    logger.warn("关闭旧的数据库连接失败", e);
                }
            }
            
            // 创建新的连接
            try {
                Class.forName("org.postgresql.Driver");
                
                java.util.Properties props = new java.util.Properties();
                props.setProperty("user", currentUsername);
                if (currentPassword != null) {
                    props.setProperty("password", currentPassword);
                }
                
                conn = DriverManager.getConnection(jdbcUrl, props);
                // 设置自动提交为false，以便在批量操作时使用事务
                conn.setAutoCommit(false);
                connectionCache.put(knowledgeBaseId, conn);
                lastUrlCache.put(knowledgeBaseId, jdbcUrl);
                lastUsernameCache.put(knowledgeBaseId, currentUsername);
                lastPasswordCache.put(knowledgeBaseId, currentPassword != null ? currentPassword : "");
                
                logger.debug("为知识库创建PgVector数据库连接 - 知识库ID: {}, URL: {}", 
                        knowledgeBaseId, jdbcUrl);
            } catch (Exception e) {
                logger.error("创建PgVector数据库连接失败", e);
                throw new RuntimeException("创建PgVector数据库连接失败: " + e.getMessage(), e);
            }
        }
        return conn;
    }
    
    /**
     * 根据类型获取配置
     */
    private VectorDatabase getConfigByType(String type) {
        if (vectorDatabaseRepository == null) {
            return null;
        }
        try {
            Optional<VectorDatabase> defaultConfig = vectorDatabaseRepository.findDefaultEnabledByType(type);
            if (defaultConfig.isPresent()) {
                return defaultConfig.get();
            }
            List<VectorDatabase> enabledConfigs = vectorDatabaseRepository.findAllEnabledByType(type);
            if (!enabledConfigs.isEmpty()) {
                return enabledConfigs.get(0);
            }
            List<VectorDatabase> allConfigs = vectorDatabaseRepository.findByType(type);
            if (!allConfigs.isEmpty()) {
                return allConfigs.get(0);
            }
        } catch (Exception e) {
            logger.warn("获取{}配置失败: {}", type, e.getMessage());
        }
        return null;
    }
    
    /**
     * 获取表名
     */
    private String getTableName(Long knowledgeBaseId) {
        return "kb_vectors_" + knowledgeBaseId;
    }
    
    /**
     * 确保集合（表）存在
     */
    @Override
    public void ensureCollection(Long knowledgeBaseId, int vectorSize) {
        String tableName = getTableName(knowledgeBaseId);
        
        try {
            Connection conn = getConnection(knowledgeBaseId);
            
            // 检查pgvector扩展是否存在
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'vector')")) {
                
                if (rs.next() && !rs.getBoolean(1)) {
                    throw new RuntimeException(
                        "pgvector扩展未安装。请执行以下SQL安装扩展：\n" +
                        "CREATE EXTENSION IF NOT EXISTS vector;");
                }
            }
            
            // 检查表是否存在
            boolean tableExists = false;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_name = '" + tableName + "')")) {
                
                if (rs.next()) {
                    tableExists = rs.getBoolean(1);
                }
            }
            
            if (!tableExists) {
                // 创建表
                String createTableSql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s (" +
                    "id BIGSERIAL PRIMARY KEY, " +
                    "document_id BIGINT NOT NULL, " +
                    "chunk_index INTEGER NOT NULL, " +
                    "text TEXT NOT NULL, " +
                    "embedding vector(%d) NOT NULL, " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")", tableName, vectorSize);
                
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createTableSql);
                    conn.commit(); // 提交表的创建
                    logger.info("创建PgVector表成功 - 知识库ID: {}, 表名: {}, 向量维度: {}", 
                            knowledgeBaseId, tableName, vectorSize);
                } catch (SQLException e) {
                    conn.rollback(); // 如果失败，回滚
                    throw e;
                }
                
                // 创建索引以提高搜索性能
                // IVFFlat 索引最多支持 2000 维，超过 2000 维需要使用 HNSW 索引或不创建索引
                if (vectorSize <= 2000) {
                    // 使用 IVFFlat 索引（适合 <= 2000 维）
                    String createIndexSql = String.format(
                        "CREATE INDEX IF NOT EXISTS %s_embedding_idx ON %s USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)",
                        tableName, tableName);
                    
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(createIndexSql);
                        conn.commit(); // 提交索引创建
                        logger.debug("创建PgVector IVFFlat索引成功 - 知识库ID: {}, 表名: {}, 向量维度: {}", 
                                knowledgeBaseId, tableName, vectorSize);
                    } catch (SQLException e) {
                        // 如果创建索引失败（比如维度限制），回滚并记录警告
                        try {
                            conn.rollback();
                        } catch (SQLException rollbackEx) {
                            logger.warn("回滚事务失败", rollbackEx);
                        }
                        logger.warn("无法创建IVFFlat索引，将使用顺序扫描 - 知识库ID: {}, 表名: {}, 向量维度: {}, 错误: {}", 
                                knowledgeBaseId, tableName, vectorSize, e.getMessage());
                    }
                } else {
                    // 对于超过 2000 维的向量，尝试使用 HNSW 索引（pgvector 0.5.0+ 支持）
                    // 如果 HNSW 不支持，则不创建索引（使用顺序扫描）
                    try {
                        String createIndexSql = String.format(
                            "CREATE INDEX IF NOT EXISTS %s_embedding_idx ON %s USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)",
                            tableName, tableName);
                        
                        try (Statement stmt = conn.createStatement()) {
                            stmt.execute(createIndexSql);
                            conn.commit(); // 提交索引创建
                            logger.debug("创建PgVector HNSW索引成功 - 知识库ID: {}, 表名: {}, 向量维度: {}", 
                                    knowledgeBaseId, tableName, vectorSize);
                        }
                    } catch (SQLException e) {
                        // HNSW 可能不支持（旧版本的 pgvector），回滚并记录警告
                        try {
                            conn.rollback();
                        } catch (SQLException rollbackEx) {
                            logger.warn("回滚事务失败", rollbackEx);
                        }
                        logger.warn("无法创建HNSW索引（可能pgvector版本不支持），将使用顺序扫描 - 知识库ID: {}, 表名: {}, 向量维度: {}, 错误: {}", 
                                knowledgeBaseId, tableName, vectorSize, e.getMessage());
                    }
                }
                
                // 创建document_id索引
                String createDocIndexSql = String.format(
                    "CREATE INDEX IF NOT EXISTS %s_document_id_idx ON %s (document_id)",
                    tableName, tableName);
                
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createDocIndexSql);
                    conn.commit(); // 提交索引创建
                } catch (SQLException e) {
                    // 如果创建索引失败，回滚并记录警告，但不影响表的使用
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        logger.warn("回滚事务失败", rollbackEx);
                    }
                    logger.warn("无法创建document_id索引 - 知识库ID: {}, 表名: {}, 错误: {}", 
                            knowledgeBaseId, tableName, e.getMessage());
                }
            } else {
                // 检查向量维度是否匹配
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                         String.format(
                             "SELECT atttypmod FROM pg_attribute " +
                             "WHERE attrelid = '%s'::regclass AND attname = 'embedding'",
                             tableName))) {
                    
                    if (rs.next()) {
                        int existingSize = rs.getInt(1) - 4; // pgvector存储维度需要减去4
                        if (existingSize != vectorSize) {
                            throw new IllegalArgumentException(
                                String.format("向量维度不匹配 - 知识库ID: %d, 期望: %d, 实际: %d", 
                                        knowledgeBaseId, vectorSize, existingSize));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("确保PgVector集合存在失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new RuntimeException("确保PgVector集合存在失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 批量插入/更新向量
     */
    @Override
    public void upsertVectors(Long knowledgeBaseId, Long documentId, 
                               List<List<Float>> vectors, List<String> texts, 
                               List<Integer> chunkIndices) {
        if (vectors == null || vectors.isEmpty()) {
            return;
        }
        
        String tableName = getTableName(knowledgeBaseId);
        
        try {
            Connection conn = getConnection(knowledgeBaseId);
            
            // 先检查表是否存在
            boolean tableExists = false;
            try (Statement checkStmt = conn.createStatement();
                 ResultSet rs = checkStmt.executeQuery(
                     "SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_name = '" + tableName + "')")) {
                
                if (rs.next()) {
                    tableExists = rs.getBoolean(1);
                }
            }
            
            // 如果表存在，先删除该文档的旧向量
            if (tableExists) {
                String deleteSql = String.format(
                    "DELETE FROM %s WHERE document_id = ?", tableName);
                
                try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                    deleteStmt.setLong(1, documentId);
                    deleteStmt.executeUpdate();
                }
            }
            
            // 批量插入新向量
            String insertSql = String.format(
                "INSERT INTO %s (document_id, chunk_index, text, embedding) VALUES (?, ?, ?, ?::vector)",
                tableName);
            
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (int i = 0; i < vectors.size(); i++) {
                    List<Float> vector = vectors.get(i);
                    String text = i < texts.size() ? texts.get(i) : "";
                    Integer chunkIndex = i < chunkIndices.size() ? chunkIndices.get(i) : i;
                    
                    // 构建向量字符串：'[1.0,2.0,3.0]'
                    StringBuilder vectorStr = new StringBuilder("[");
                    for (int j = 0; j < vector.size(); j++) {
                        if (j > 0) {
                            vectorStr.append(",");
                        }
                        vectorStr.append(vector.get(j));
                    }
                    vectorStr.append("]");
                    
                    insertStmt.setLong(1, documentId);
                    insertStmt.setInt(2, chunkIndex);
                    insertStmt.setString(3, text);
                    insertStmt.setString(4, vectorStr.toString());
                    insertStmt.addBatch();
                }
                
                insertStmt.executeBatch();
                conn.commit();
                
                logger.debug("批量插入PgVector向量成功 - 知识库ID: {}, 文档ID: {}, 向量数量: {}", 
                        knowledgeBaseId, documentId, vectors.size());
            }
        } catch (SQLException e) {
            logger.error("批量插入PgVector向量失败 - 知识库ID: {}, 文档ID: {}", 
                    knowledgeBaseId, documentId, e);
            throw new RuntimeException("批量插入PgVector向量失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 搜索向量
     */
    @Override
    public List<SearchResult> searchVectors(Long knowledgeBaseId, List<Float> queryVector, int topK) {
        if (queryVector == null || queryVector.isEmpty()) {
            return new ArrayList<>();
        }
        
        String tableName = getTableName(knowledgeBaseId);
        
        try {
            Connection conn = getConnection(knowledgeBaseId);
            
            // 构建查询向量字符串
            StringBuilder vectorStr = new StringBuilder("[");
            for (int i = 0; i < queryVector.size(); i++) {
                if (i > 0) {
                    vectorStr.append(",");
                }
                vectorStr.append(queryVector.get(i));
            }
            vectorStr.append("]");
            
            // 使用余弦相似度搜索
            String searchSql = String.format(
                "SELECT document_id, chunk_index, text, " +
                "1 - (embedding <=> ?::vector) AS similarity " +
                "FROM %s " +
                "ORDER BY embedding <=> ?::vector " +
                "LIMIT ?",
                tableName);
            
            List<SearchResult> results = new ArrayList<>();
            
            try (PreparedStatement stmt = conn.prepareStatement(searchSql)) {
                stmt.setString(1, vectorStr.toString());
                stmt.setString(2, vectorStr.toString());
                stmt.setInt(3, topK);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        SearchResult result = new SearchResult();
                        result.setDocumentId(rs.getLong("document_id"));
                        result.setChunkIndex(rs.getInt("chunk_index"));
                        result.setText(rs.getString("text"));
                        result.setScore(rs.getDouble("similarity"));
                        results.add(result);
                    }
                }
            }
            
            logger.debug("PgVector向量搜索成功 - 知识库ID: {}, 返回结果数: {}", 
                    knowledgeBaseId, results.size());
            
            return results;
        } catch (SQLException e) {
            logger.error("PgVector向量搜索失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new RuntimeException("PgVector向量搜索失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除文档的所有向量
     */
    @Override
    public void deleteDocumentVectors(Long knowledgeBaseId, Long documentId) {
        String tableName = getTableName(knowledgeBaseId);
        
        try {
            Connection conn = getConnection(knowledgeBaseId);
            
            // 先检查表是否存在
            boolean tableExists = false;
            try (Statement checkStmt = conn.createStatement();
                 ResultSet rs = checkStmt.executeQuery(
                     "SELECT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_name = '" + tableName + "')")) {
                
                if (rs.next()) {
                    tableExists = rs.getBoolean(1);
                }
            }
            
            if (!tableExists) {
                logger.debug("表不存在，跳过删除操作 - 知识库ID: {}, 文档ID: {}, 表名: {}", 
                        knowledgeBaseId, documentId, tableName);
                return;
            }
            
            String deleteSql = String.format(
                "DELETE FROM %s WHERE document_id = ?", tableName);
            
            try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                stmt.setLong(1, documentId);
                int deleted = stmt.executeUpdate();
                conn.commit();
                
                logger.debug("删除PgVector向量成功 - 知识库ID: {}, 文档ID: {}, 删除数量: {}", 
                        knowledgeBaseId, documentId, deleted);
            }
        } catch (SQLException e) {
            // 如果是表不存在的错误，优雅处理
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("does not exist")) {
                logger.debug("表不存在，跳过删除操作 - 知识库ID: {}, 文档ID: {}, 表名: {}", 
                        knowledgeBaseId, documentId, tableName);
                return;
            }
            logger.error("删除PgVector向量失败 - 知识库ID: {}, 文档ID: {}", 
                    knowledgeBaseId, documentId, e);
            throw new RuntimeException("删除PgVector向量失败: " + e.getMessage(), e);
        }
    }
}

