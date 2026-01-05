package com.github.app.dify.knowledgebase.service.strategy;

import com.github.app.dify.knowledgebase.domain.VectorDatabase;
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
    
    @Autowired
    private com.github.app.dify.knowledgebase.util.VectorDatabaseConfigHelper configHelper;
    
    @Autowired(required = false)
    private com.github.app.dify.system.config.DocumentReaderConfig documentReaderConfig;
    
    @Autowired(required = false)
    private com.github.app.dify.knowledgebase.repository.VectorDatabaseRepository vectorDatabaseRepository;
    
    @Autowired(required = false)
    private com.github.app.dify.knowledgebase.repository.KnowledgeBaseRepository knowledgeBaseRepository;
    
    // 为每个知识库缓存数据库连接
    private final Map<Long, Connection> connectionCache = new ConcurrentHashMap<>();
    private final Map<Long, String> lastUrlCache = new ConcurrentHashMap<>();
    private final Map<Long, String> lastUsernameCache = new ConcurrentHashMap<>();
    private final Map<Long, String> lastPasswordCache = new ConcurrentHashMap<>();
    
    /**
     * 获取知识库的向量数据库配置
     * 优先从vectorDatabaseId获取具体实例，如果没有则使用类型查找
     */
    private VectorDatabase getVectorDatabaseConfig(Long knowledgeBaseId) {
        try {
            // 如果是文档解读（knowledgeBaseId为0），从DocumentReaderConfig读取vectorDatabaseId
            if (knowledgeBaseId != null && knowledgeBaseId == 0L && documentReaderConfig != null) {
                Long vectorDatabaseId = documentReaderConfig.getVectorDatabaseId();
                if (vectorDatabaseId != null && vectorDatabaseRepository != null) {
                    java.util.Optional<VectorDatabase> config = vectorDatabaseRepository.findById(vectorDatabaseId);
                    if (config.isPresent()) {
                        logger.debug("从文档解读配置读取向量数据库配置 - 配置ID: {}", vectorDatabaseId);
                        return config.get();
                    } else {
                        logger.warn("文档解读配置的向量数据库ID不存在: {}, 使用类型配置", vectorDatabaseId);
                    }
                }
            }
            
            // 从知识库读取vectorDatabaseId
            if (knowledgeBaseRepository != null) {
                java.util.Optional<com.github.app.dify.knowledgebase.domain.KnowledgeBase> kb = 
                        knowledgeBaseRepository.findById(knowledgeBaseId);
                if (kb.isPresent() && kb.get().getVectorDatabaseId() != null) {
                    Long vectorDatabaseId = kb.get().getVectorDatabaseId();
                    if (vectorDatabaseRepository != null) {
                        java.util.Optional<VectorDatabase> config = vectorDatabaseRepository.findById(vectorDatabaseId);
                        if (config.isPresent()) {
                            logger.debug("从知识库读取向量数据库配置 - 知识库ID: {}, 配置ID: {}", 
                                    knowledgeBaseId, vectorDatabaseId);
                            return config.get();
                        }
                    }
                }
            }
            
            // 如果没有指定配置，使用默认的pgvector配置
            VectorDatabase defaultConfig = configHelper.getConfigByType("pgvector");
            if (defaultConfig != null) {
                logger.debug("使用默认pgvector配置 - 知识库ID: {}", knowledgeBaseId);
                return defaultConfig;
            }
        } catch (Exception e) {
            logger.warn("获取向量数据库配置失败 - 知识库ID: {}", knowledgeBaseId, e);
        }
        
        return null;
    }
    
    /**
     * 获取指定知识库的数据库连接
     */
    private Connection getConnection(Long knowledgeBaseId) {
        // 获取对应的配置
        String currentUrl;
        String currentUsername;
        String currentPassword;
        
        VectorDatabase config = getVectorDatabaseConfig(knowledgeBaseId);
        if (config != null) {
            currentUrl = config.getUrl();
            // 使用工具类提取用户名和密码
            String[] credentials = configHelper.extractUsernamePassword(config);
            if (credentials != null) {
                currentUsername = credentials[0];
                currentPassword = credentials[1];
            } else {
                currentUsername = null;
                currentPassword = null;
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
                // 对于文档解读（knowledgeBaseId=0），添加user_id字段以支持用户隔离
                String createTableSql;
                if (knowledgeBaseId != null && knowledgeBaseId == 0L) {
                    // 文档解读表：包含user_id字段
                    createTableSql = String.format(
                        "CREATE TABLE IF NOT EXISTS %s (" +
                        "id BIGSERIAL PRIMARY KEY, " +
                        "document_id BIGINT NOT NULL, " +
                        "user_id BIGINT, " +
                        "chunk_index INTEGER NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "embedding vector(%d) NOT NULL, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")", tableName, vectorSize);
                } else {
                    // 知识库表：不包含user_id字段
                    createTableSql = String.format(
                        "CREATE TABLE IF NOT EXISTS %s (" +
                        "id BIGSERIAL PRIMARY KEY, " +
                        "document_id BIGINT NOT NULL, " +
                        "chunk_index INTEGER NOT NULL, " +
                        "text TEXT NOT NULL, " +
                        "embedding vector(%d) NOT NULL, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")", tableName, vectorSize);
                }
                
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
                
                // 对于文档解读（knowledgeBaseId=0），创建user_id索引
                if (knowledgeBaseId != null && knowledgeBaseId == 0L) {
                    try (Statement stmt = conn.createStatement()) {
                        // 检查表是否有user_id字段
                        boolean hasUserIdColumn = false;
                        try (Statement checkStmt = conn.createStatement();
                             ResultSet rs = checkStmt.executeQuery(
                                 "SELECT column_name FROM information_schema.columns " +
                                 "WHERE table_name = '" + tableName + "' AND column_name = 'user_id'")) {
                            hasUserIdColumn = rs.next();
                        }
                        
                        if (hasUserIdColumn) {
                            String createUserIdIndexSql = String.format(
                                "CREATE INDEX IF NOT EXISTS %s_user_id_idx ON %s (user_id)",
                                tableName, tableName);
                            stmt.execute(createUserIdIndexSql);
                            conn.commit();
                            logger.debug("创建PgVector user_id索引成功 - 知识库ID: {}, 表名: {}", 
                                    knowledgeBaseId, tableName);
                        }
                    } catch (SQLException e) {
                        logger.warn("创建user_id索引失败 - 知识库ID: {}, 表名: {}, 错误: {}", 
                                knowledgeBaseId, tableName, e.getMessage());
                    }
                }
                
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
                        int atttypmod = rs.getInt(1);
                        // pgvector的维度计算：atttypmod - 4 或使用更准确的方法
                        // 如果atttypmod为-1（未指定），尝试从实际数据中获取维度
                        int existingSize;
                        if (atttypmod == -1) {
                            // 如果atttypmod为-1，尝试从表中实际数据获取维度
                            try (Statement dimStmt = conn.createStatement();
                                 ResultSet dimRs = dimStmt.executeQuery(
                                     String.format(
                                         "SELECT array_length(embedding::float[], 1) as dim FROM %s LIMIT 1",
                                         tableName))) {
                                if (dimRs.next()) {
                                    existingSize = dimRs.getInt(1);
                                } else {
                                    // 表中没有数据，使用传入的维度
                                    existingSize = vectorSize;
                                }
                            }
                        } else {
                            // pgvector存储维度：atttypmod - 4
                            existingSize = atttypmod - 4;
                        }
                        
                        if (existingSize != vectorSize) {
                            // 检查表中是否有数据
                            boolean hasData = false;
                            try (Statement countStmt = conn.createStatement();
                                 ResultSet countRs = countStmt.executeQuery(
                                     String.format("SELECT COUNT(*) as cnt FROM %s", tableName))) {
                                if (countRs.next()) {
                                    hasData = countRs.getInt("cnt") > 0;
                                }
                            } catch (SQLException e) {
                                logger.warn("检查表数据失败", e);
                            }
                            
                            if (!hasData) {
                                // 如果表为空，自动删除并重新创建
                                logger.warn("检测到向量维度不匹配且表为空，自动删除并重新创建表 - 知识库ID: {}, 表名: {}, 旧维度: {}, 新维度: {}", 
                                        knowledgeBaseId, tableName, existingSize, vectorSize);
                                try (Statement dropStmt = conn.createStatement()) {
                                    dropStmt.execute(String.format("DROP TABLE IF EXISTS %s CASCADE", tableName));
                                    conn.commit();
                                    logger.info("已删除旧表，将重新创建 - 知识库ID: {}, 表名: {}", knowledgeBaseId, tableName);
                                    // 重新创建表（递归调用ensureCollection）
                                    ensureCollection(knowledgeBaseId, vectorSize);
                                    return;
                                } catch (SQLException e) {
                                    logger.error("自动删除表失败 - 知识库ID: {}, 表名: {}", knowledgeBaseId, tableName, e);
                                    throw new RuntimeException("自动删除表失败: " + e.getMessage(), e);
                                }
                            } else {
                                // 如果表有数据，提供详细的错误信息和解决方案
                                String errorMsg = String.format(
                                    "向量维度不匹配 - 知识库ID: %d, 期望: %d, 实际: %d\n" +
                                    "表 %s 中已有数据，无法自动修复。请选择以下方案之一：\n" +
                                    "方案1（推荐）：使用与现有维度匹配的嵌入模型\n" +
                                    "  - 当前表维度: %d\n" +
                                    "  - 请在系统配置中设置 documentReader.defaultEmbeddingModelId 为维度 %d 的模型\n" +
                                    "方案2：删除表并重新创建（会丢失所有数据）\n" +
                                    "  - 执行SQL: DROP TABLE IF EXISTS %s CASCADE;\n" +
                                    "  - 然后重新上传文档\n" +
                                    "方案3：手动修改表结构（需要迁移数据，操作复杂）\n" +
                                    "  - 执行SQL: ALTER TABLE %s ALTER COLUMN embedding TYPE vector(%d);\n" +
                                    "  - 注意：此操作可能需要较长时间，且需要确保所有现有向量维度匹配",
                                    knowledgeBaseId, vectorSize, existingSize, 
                                    tableName, existingSize, existingSize,
                                    tableName, tableName, vectorSize);
                                logger.error(errorMsg);
                                throw new IllegalArgumentException(errorMsg);
                            }
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
        
        // 从实际向量中获取维度
        int actualVectorSize = vectors.get(0).size();
        
        // 确保集合存在且维度匹配
        ensureCollection(knowledgeBaseId, actualVectorSize);
        
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
            
            // 对于文档解读（knowledgeBaseId=0），获取userId
            // 注意：document_reader表在主数据库中，可能不在pgvector数据库中
            // 如果无法查询，userId将为null，但不影响功能（user_id字段为可选）
            Long userId = null;
            if (knowledgeBaseId != null && knowledgeBaseId == 0L) {
                try {
                    // 尝试从pgvector数据库查询document_reader表（如果存在）
                    // 如果表不存在，说明document_reader在主数据库，此时userId为null
                    // 注意：PostgreSQL表名大小写敏感，使用双引号或小写
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(
                             "SELECT user_id FROM \"DOCUMENT_READER\" WHERE id = " + documentId + " AND deleted = 0 LIMIT 1")) {
                        if (rs.next()) {
                            userId = rs.getLong("user_id");
                            if (rs.wasNull()) {
                                userId = null;
                            }
                            logger.debug("从pgvector数据库获取文档用户ID成功 - 文档ID: {}, 用户ID: {}", documentId, userId);
                        }
                    }
                } catch (SQLException e) {
                    // 如果表不存在或查询失败，说明document_reader在主数据库中
                    // 这是正常情况，userId保持为null
                    logger.debug("无法从pgvector数据库查询document_reader表（可能表在主数据库中），user_id将不设置 - 文档ID: {}", documentId);
                } catch (Exception e) {
                    logger.warn("获取文档用户ID失败，将不设置user_id字段 - 文档ID: {}", documentId, e);
                }
            }
            
            // 批量插入新向量
            // 对于文档解读（knowledgeBaseId=0），如果表有user_id字段，则包含user_id
            String insertSql;
            boolean hasUserIdColumn = false;
            if (knowledgeBaseId != null && knowledgeBaseId == 0L) {
                // 检查表是否有user_id字段
                try (Statement checkStmt = conn.createStatement();
                     ResultSet rs = checkStmt.executeQuery(
                         "SELECT column_name FROM information_schema.columns " +
                         "WHERE table_name = '" + tableName + "' AND column_name = 'user_id'")) {
                    hasUserIdColumn = rs.next();
                } catch (SQLException e) {
                    logger.debug("检查user_id字段失败，假设不存在 - 表名: {}", tableName, e);
                }
                
                if (hasUserIdColumn && userId != null) {
                    insertSql = String.format(
                        "INSERT INTO %s (document_id, user_id, chunk_index, text, embedding) VALUES (?, ?, ?, ?, ?::vector)",
                        tableName);
                } else {
                    insertSql = String.format(
                        "INSERT INTO %s (document_id, chunk_index, text, embedding) VALUES (?, ?, ?, ?::vector)",
                        tableName);
                }
            } else {
                insertSql = String.format(
                    "INSERT INTO %s (document_id, chunk_index, text, embedding) VALUES (?, ?, ?, ?::vector)",
                    tableName);
            }
            
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
                    
                    int paramIndex = 1;
                    insertStmt.setLong(paramIndex++, documentId);
                    if (hasUserIdColumn && userId != null) {
                        insertStmt.setLong(paramIndex++, userId);
                    }
                    insertStmt.setInt(paramIndex++, chunkIndex);
                    insertStmt.setString(paramIndex++, text);
                    insertStmt.setString(paramIndex, vectorStr.toString());
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

