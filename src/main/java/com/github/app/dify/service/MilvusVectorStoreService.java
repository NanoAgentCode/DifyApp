package com.github.app.dify.service;

import com.github.app.dify.config.MilvusConfig;
import com.github.app.dify.domain.VectorDatabase;
import com.github.app.dify.repository.KnowledgeBaseRepository;
import com.github.app.dify.repository.VectorDatabaseRepository;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.index.DescribeIndexParam;
import io.milvus.response.DescribeIndexResponse;
import io.milvus.response.SearchResultsWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Milvus向量存储服务（使用gRPC）
 * 支持Milvus和Milvus Lite
 */
@Service
public class MilvusVectorStoreService {
    
    private static final Logger logger = LoggerFactory.getLogger(MilvusVectorStoreService.class);
    
    @Autowired
    private MilvusConfig milvusConfig;
    
    @Autowired(required = false)
    private VectorDatabaseRepository vectorDatabaseRepository;
    
    @Autowired(required = false)
    private KnowledgeBaseRepository knowledgeBaseRepository;
    
    // 为每个知识库缓存Milvus客户端（因为不同知识库可能使用不同的配置）
    private final Map<Long, MilvusServiceClient> clientCache = new HashMap<>();
    private final Map<Long, String> lastHostCache = new HashMap<>();
    private final Map<Long, Integer> lastPortCache = new HashMap<>();
    private final Map<Long, String> lastApiKeyCache = new HashMap<>();
    
    /**
     * 获取指定知识库的Milvus客户端
     */
    private MilvusServiceClient getMilvusClient(Long knowledgeBaseId) {
        // 获取知识库的向量存储类型
        String vectorStoreType = getVectorStoreType(knowledgeBaseId);
        
        // 获取对应的配置（Milvus 和 Milvus Lite 兼容，使用相同的配置）
        String currentUrl;
        String currentApiKey;
        
        // Milvus Lite 和 Milvus 完全兼容，优先查找对应类型的配置，如果没有则使用 Milvus 配置
        if ("milvus-lite".equalsIgnoreCase(vectorStoreType)) {
            // Milvus Lite：先尝试从数据库获取 milvus-lite 配置
            VectorDatabase config = getConfigByType("milvus-lite");
            if (config != null) {
                currentUrl = config.getUrl();
                currentApiKey = config.getApiKey();
            } else {
                // 如果没有 milvus-lite 配置，尝试使用 milvus 配置（两者兼容）
                config = getConfigByType("milvus");
                if (config != null) {
                    currentUrl = config.getUrl();
                    currentApiKey = config.getApiKey();
                } else {
                    // 如果都没有，使用默认的 MilvusConfig
                    currentUrl = milvusConfig.getUrl();
                    currentApiKey = milvusConfig.getApiKey();
                }
            }
        } else {
            // Milvus：使用 MilvusConfig 或数据库中的 milvus 配置
            VectorDatabase config = getConfigByType("milvus");
            if (config != null) {
                currentUrl = config.getUrl();
                currentApiKey = config.getApiKey();
            } else {
                currentUrl = milvusConfig.getUrl();
                currentApiKey = milvusConfig.getApiKey();
            }
        }
        
        // 解析URL，提取主机和端口
        String host = "localhost";
        int port = 19530;
        
        if (currentUrl != null && !currentUrl.trim().isEmpty()) {
            try {
                // 支持 http://host:port 或 host:port 格式
                String urlStr = currentUrl.trim();
                if (urlStr.startsWith("http://")) {
                    urlStr = urlStr.substring(7);
                } else if (urlStr.startsWith("https://")) {
                    urlStr = urlStr.substring(8);
                }
                
                if (urlStr.contains(":")) {
                    String[] parts = urlStr.split(":");
                    host = parts[0];
                    port = Integer.parseInt(parts[1]);
                } else {
                    host = urlStr;
                }
            } catch (Exception e) {
                logger.warn("解析Milvus URL失败: {}, 使用默认值 localhost:19530", currentUrl, e);
            }
        }
        
        // 检查缓存
        String lastHost = lastHostCache.get(knowledgeBaseId);
        Integer lastPort = lastPortCache.get(knowledgeBaseId);
        String lastApiKey = lastApiKeyCache.get(knowledgeBaseId);
        MilvusServiceClient client = clientCache.get(knowledgeBaseId);
        
        if (client == null || 
            !host.equals(lastHost) || 
            port != (lastPort != null ? lastPort : 19530) ||
            (currentApiKey != null ? !currentApiKey.equals(lastApiKey) : lastApiKey != null)) {
            
            // 关闭旧的客户端
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    logger.warn("关闭旧的Milvus客户端失败", e);
                }
            }
            
            // 创建新的客户端
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .build();
            
            // 注意：Milvus Java SDK 2.3.4 可能不支持用户名/密码认证
            // 如果需要认证，可能需要使用其他方式或更新 SDK 版本
            client = new MilvusServiceClient(connectParam);
            
            clientCache.put(knowledgeBaseId, client);
            lastHostCache.put(knowledgeBaseId, host);
            lastPortCache.put(knowledgeBaseId, port);
            lastApiKeyCache.put(knowledgeBaseId, currentApiKey);
            
            logger.debug("为知识库创建Milvus客户端 - 知识库ID: {}, 类型: {}, 主机: {}, 端口: {}", 
                    knowledgeBaseId, vectorStoreType, host, port);
        }
        return client;
    }
    
    /**
     * 获取知识库的向量存储类型
     */
    private String getVectorStoreType(Long knowledgeBaseId) {
        if (knowledgeBaseRepository == null) {
            return "milvus"; // 默认
        }
        try {
            return knowledgeBaseRepository.findById(knowledgeBaseId)
                    .map(kb -> {
                        String type = kb.getVectorStoreType();
                        if (type != null && ("milvus".equalsIgnoreCase(type) || "milvus-lite".equalsIgnoreCase(type))) {
                            return type;
                        }
                        return "milvus"; // 默认
                    })
                    .orElse("milvus");
        } catch (Exception e) {
            logger.warn("获取知识库向量存储类型失败，使用默认值milvus - 知识库ID: {}", knowledgeBaseId, e);
            return "milvus";
        }
    }
    
    /**
     * 根据类型获取配置
     */
    private VectorDatabase getConfigByType(String type) {
        if (vectorDatabaseRepository == null) {
            return null;
        }
        try {
            // 先尝试查找默认的启用配置
            Optional<VectorDatabase> defaultConfig = vectorDatabaseRepository.findDefaultEnabledByType(type);
            if (defaultConfig.isPresent()) {
                return defaultConfig.get();
            }
            
            // 如果没有默认配置，尝试查找第一个启用的配置
            List<VectorDatabase> enabledConfigs = vectorDatabaseRepository.findAllEnabledByType(type);
            if (!enabledConfigs.isEmpty()) {
                return enabledConfigs.get(0);
            }
            
            // 如果连启用的配置都没有，尝试查找任何配置（包括未启用的）
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
     * 获取指定知识库的超时时间
     */
    private int getTimeout(Long knowledgeBaseId) {
        String vectorStoreType = getVectorStoreType(knowledgeBaseId);
        // Milvus 和 Milvus Lite 兼容，优先查找对应类型的配置
        VectorDatabase config = null;
        if ("milvus-lite".equalsIgnoreCase(vectorStoreType)) {
            config = getConfigByType("milvus-lite");
            if (config == null) {
                // 如果没有 milvus-lite 配置，尝试使用 milvus 配置
                config = getConfigByType("milvus");
            }
        } else {
            config = getConfigByType("milvus");
        }
        
        if (config != null && config.getTimeout() != null) {
            return config.getTimeout();
        }
        return milvusConfig.getTimeout();
    }
    
    /**
     * 确保集合存在
     */
    public void ensureCollection(Long knowledgeBaseId, int vectorSize) {
        String collectionName = getCollectionName(knowledgeBaseId);
        
        try {
            // 检查集合是否存在
            if (collectionExists(knowledgeBaseId, collectionName)) {
                // 集合存在，检查配置是否匹配（简化处理，只检查是否存在）
                logger.debug("Milvus集合已存在 - 知识库ID: {}, 集合名: {}", 
                        knowledgeBaseId, collectionName);
                return;
            }
            
            // 创建集合
            createCollection(knowledgeBaseId, collectionName, vectorSize);
            
        } catch (Exception e) {
            logger.error("确保Milvus集合存在失败 - 知识库ID: {}", knowledgeBaseId, e);
            throw new RuntimeException("确保Milvus集合存在失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建集合
     */
    private void createCollection(Long knowledgeBaseId, String collectionName, int vectorSize) {
        try {
            MilvusServiceClient client = getMilvusClient(knowledgeBaseId);
            
            // 定义字段
            List<FieldType> fields = new ArrayList<>();
            
            // ID字段
            FieldType idField = FieldType.newBuilder()
                    .withName("id")
                    .withDataType(DataType.Int64)
                    .withPrimaryKey(true)
                    .withAutoID(false)
                    .build();
            fields.add(idField);
            
            // 向量字段
            FieldType vectorField = FieldType.newBuilder()
                    .withName("vector")
                    .withDataType(DataType.FloatVector)
                    .withDimension(vectorSize)
                    .build();
            fields.add(vectorField);
            
            // document_id字段
            FieldType docIdField = FieldType.newBuilder()
                    .withName("document_id")
                    .withDataType(DataType.Int64)
                    .build();
            fields.add(docIdField);
            
            // chunk_index字段
            FieldType chunkIndexField = FieldType.newBuilder()
                    .withName("chunk_index")
                    .withDataType(DataType.Int32)
                    .build();
            fields.add(chunkIndexField);
            
            // text字段
            FieldType textField = FieldType.newBuilder()
                    .withName("text")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(65535)
                    .build();
            fields.add(textField);
            
            // knowledge_base_id字段
            FieldType kbIdField = FieldType.newBuilder()
                    .withName("knowledge_base_id")
                    .withDataType(DataType.Int64)
                    .build();
            fields.add(kbIdField);
            
            // 创建集合
            CreateCollectionParam.Builder createBuilder = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withDescription("Knowledge base collection: " + collectionName);
            
            for (FieldType field : fields) {
                createBuilder.addFieldType(field);
            }
            
            CreateCollectionParam createParam = createBuilder.build();
            
            logger.debug("创建Milvus集合 - 集合名: {}, 向量维度: {}", collectionName, vectorSize);
            
            R<?> response = client.createCollection(createParam);
            if (response.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("创建Milvus集合失败: " + response.getMessage());
            }
            
            logger.info("创建Milvus集合成功 - 集合名: {}, 向量维度: {}", collectionName, vectorSize);
            
            // 注意：索引应该在插入数据后创建，所以这里不创建索引
            // 集合会在插入数据后自动加载
            
        } catch (Exception e) {
            logger.error("创建Milvus集合失败 - 集合名: {}", collectionName, e);
            throw new RuntimeException("创建Milvus集合失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建索引
     */
    private void createIndex(Long knowledgeBaseId, String collectionName) {
        try {
            MilvusServiceClient client = getMilvusClient(knowledgeBaseId);
            
            // 创建索引参数
            Map<String, Object> indexParams = new HashMap<>();
            indexParams.put("nlist", 1024);
            
            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName("vector")
                    .withIndexType(io.milvus.param.IndexType.IVF_FLAT)
                    .withMetricType(io.milvus.param.MetricType.COSINE)
                    .withExtraParam("{\"nlist\":1024}")
                    .withSyncMode(Boolean.FALSE)
                    .build();
            
            R<?> response = client.createIndex(indexParam);
            if (response.getStatus() != R.Status.Success.getCode()) {
                logger.warn("创建Milvus索引失败，但继续执行 - 集合名: {}, 错误: {}", 
                        collectionName, response.getMessage());
            } else {
                logger.info("创建Milvus索引成功 - 集合名: {}", collectionName);
            }
        } catch (Exception e) {
            logger.warn("创建Milvus索引失败，但继续执行 - 集合名: {}", collectionName, e);
        }
    }
    
    /**
     * 刷新集合数据
     */
    private void flushCollection(Long knowledgeBaseId, String collectionName) {
        try {
            MilvusServiceClient client = getMilvusClient(knowledgeBaseId);
            
            FlushParam flushParam = FlushParam.newBuilder()
                    .withCollectionNames(Collections.singletonList(collectionName))
                    .build();
            
            R<?> response = client.flush(flushParam);
            if (response.getStatus() != R.Status.Success.getCode()) {
                logger.warn("刷新Milvus集合失败，但继续执行 - 集合名: {}, 错误: {}", 
                        collectionName, response.getMessage());
            } else {
                logger.debug("刷新Milvus集合成功 - 集合名: {}", collectionName);
            }
        } catch (Exception e) {
            logger.warn("刷新Milvus集合失败，但继续执行 - 集合名: {}", collectionName, e);
        }
    }
    
    /**
     * 确保索引存在
     */
    private void ensureIndexExists(Long knowledgeBaseId, String collectionName) {
        try {
            MilvusServiceClient client = getMilvusClient(knowledgeBaseId);
            
            // 检查索引是否存在
            DescribeIndexParam describeParam = DescribeIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName("vector")
                    .build();
            
            R<DescribeIndexResponse> describeResponse = client.describeIndex(describeParam);
            if (describeResponse.getStatus() == R.Status.Success.getCode() && 
                describeResponse.getData() != null && 
                describeResponse.getData().getIndexDescriptions() != null &&
                !describeResponse.getData().getIndexDescriptions().isEmpty()) {
                // 索引已存在
                logger.debug("Milvus索引已存在 - 集合名: {}", collectionName);
                return;
            }
            
            // 索引不存在，创建索引
            logger.info("Milvus索引不存在，开始创建索引 - 集合名: {}", collectionName);
            createIndex(knowledgeBaseId, collectionName);
        } catch (Exception e) {
            logger.warn("检查Milvus索引失败，尝试创建索引 - 集合名: {}", collectionName, e);
            // 如果检查失败，尝试直接创建索引
            createIndex(knowledgeBaseId, collectionName);
        }
    }
    
    /**
     * 加载集合
     */
    private void loadCollection(Long knowledgeBaseId, String collectionName) {
        try {
            MilvusServiceClient client = getMilvusClient(knowledgeBaseId);
            
            LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();
            
            R<?> response = client.loadCollection(loadParam);
            if (response.getStatus() != R.Status.Success.getCode()) {
                logger.warn("加载Milvus集合失败，但继续执行 - 集合名: {}, 错误: {}", 
                        collectionName, response.getMessage());
            } else {
                logger.debug("加载Milvus集合成功 - 集合名: {}", collectionName);
            }
        } catch (Exception e) {
            logger.warn("加载Milvus集合失败，但继续执行 - 集合名: {}", collectionName, e);
        }
    }
    
    /**
     * 检查集合是否存在
     */
    private boolean collectionExists(Long knowledgeBaseId, String collectionName) {
        try {
            MilvusServiceClient client = getMilvusClient(knowledgeBaseId);
            
            HasCollectionParam param = HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();
            
            R<Boolean> response = client.hasCollection(param);
            if (response.getStatus() != R.Status.Success.getCode()) {
                logger.warn("检查集合存在性失败 - 集合名: {}, 错误: {}", collectionName, response.getMessage());
                return false;
            }
            
            return response.getData() != null && response.getData();
        } catch (Exception e) {
            logger.warn("检查集合存在性失败 - 集合名: {}", collectionName, e);
            return false;
        }
    }
    
    /**
     * 批量插入/更新向量
     */
    public void upsertVectors(Long knowledgeBaseId, Long documentId, 
                              List<List<Float>> vectors, List<String> texts, 
                              List<Integer> chunkIndices) {
        if (vectors == null || vectors.isEmpty()) {
            logger.warn("向量列表为空，跳过插入 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            return;
        }
        
        String collectionName = getCollectionName(knowledgeBaseId);
        
        try {
            // 先删除该文档的旧向量
            deleteDocumentVectors(knowledgeBaseId, documentId);
            
            MilvusServiceClient client = getMilvusClient(knowledgeBaseId);
            
            // 准备插入数据
            List<Long> ids = new ArrayList<>();
            List<List<Float>> vectorList = new ArrayList<>();
            List<Long> documentIds = new ArrayList<>();
            List<Integer> chunkIndexList = new ArrayList<>();
            List<String> textList = new ArrayList<>();
            List<Long> knowledgeBaseIdList = new ArrayList<>();
            
            for (int i = 0; i < vectors.size(); i++) {
                List<Float> vector = vectors.get(i);
                String text = i < texts.size() ? texts.get(i) : "";
                int chunkIndex = i < chunkIndices.size() ? chunkIndices.get(i) : i;
                
                // 生成唯一ID
                long id = generateId(knowledgeBaseId, documentId, chunkIndex);
                ids.add(id);
                vectorList.add(vector);
                documentIds.add(documentId);
                chunkIndexList.add(chunkIndex);
                textList.add(text);
                knowledgeBaseIdList.add(knowledgeBaseId);
            }
            
            // 构建插入数据
            List<InsertParam.Field> fields = new ArrayList<>();
            fields.add(new InsertParam.Field("id", ids));
            fields.add(new InsertParam.Field("vector", vectorList));
            fields.add(new InsertParam.Field("document_id", documentIds));
            fields.add(new InsertParam.Field("chunk_index", chunkIndexList));
            fields.add(new InsertParam.Field("text", textList));
            fields.add(new InsertParam.Field("knowledge_base_id", knowledgeBaseIdList));
            
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(fields)
                    .build();
            
            logger.debug("发送Milvus向量插入请求 - 知识库ID: {}, 文档ID: {}, 集合名: {}, 向量数量: {}", 
                    knowledgeBaseId, documentId, collectionName, vectors.size());
            
            R<?> response = client.insert(insertParam);
            if (response.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("插入Milvus向量失败: " + response.getMessage());
            }
            
            logger.info("Milvus向量插入完成 - 知识库ID: {}, 文档ID: {}, 向量数量: {}", 
                    knowledgeBaseId, documentId, vectors.size());
            
            // 刷新数据，确保数据可以被搜索
            flushCollection(knowledgeBaseId, collectionName);
            
            // 如果集合还没有索引，创建索引
            // 注意：只有在集合有数据时才能创建索引
            ensureIndexExists(knowledgeBaseId, collectionName);
            
            // 确保集合已加载
            loadCollection(knowledgeBaseId, collectionName);
            
        } catch (Exception e) {
            logger.error("向量插入失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
            throw new RuntimeException("向量插入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 向量检索
     */
    public List<SearchResult> searchVectors(Long knowledgeBaseId, List<Float> queryVector, int topK) {
        String collectionName = getCollectionName(knowledgeBaseId);
        
        // 检查集合是否存在
        if (!collectionExists(knowledgeBaseId, collectionName)) {
            logger.warn("Milvus集合不存在 - 知识库ID: {}, 集合名: {}, 返回空结果", 
                    knowledgeBaseId, collectionName);
            return new ArrayList<>();
        }
        
        // 检查查询向量是否为空
        if (queryVector == null || queryVector.isEmpty()) {
            logger.warn("查询向量为空 - 知识库ID: {}, 返回空结果", knowledgeBaseId);
            return new ArrayList<>();
        }
        
        try {
            MilvusServiceClient client = getMilvusClient(knowledgeBaseId);
            
            // 确保集合已加载
            loadCollection(knowledgeBaseId, collectionName);
            
            // 构建搜索参数
            List<String> outputFields = Arrays.asList("text", "document_id", "chunk_index");
            List<List<Float>> searchVectors = Collections.singletonList(queryVector);
            
            String searchParams = "{\"nprobe\":10}";
            
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(io.milvus.param.MetricType.COSINE)
                    .withOutFields(outputFields)
                    .withTopK(topK)
                    .withVectors(searchVectors)
                    .withVectorFieldName("vector")
                    .withParams(searchParams)
                    .build();
            
            logger.debug("发送Milvus搜索请求 - 知识库ID: {}, 集合名: {}, 向量维度: {}, topK: {}", 
                    knowledgeBaseId, collectionName, queryVector.size(), topK);
            
            R<?> response = client.search(searchParam);
            if (response.getStatus() != R.Status.Success.getCode()) {
                throw new RuntimeException("Milvus搜索失败: " + response.getMessage());
            }
            
            List<SearchResult> results = new ArrayList<>();
            
            // 获取搜索结果
            // 注意：由于 Milvus Java SDK 版本差异，这里使用反射来兼容不同版本
            Object data = response.getData();
            if (data != null) {
                try {
                    // 尝试获取 Results 字段
                    java.lang.reflect.Method getResultsMethod = data.getClass().getMethod("getResults");
                    Object resultsObj = getResultsMethod.invoke(data);
                    
                    // 使用 SearchResultsWrapper 包装结果
                    // 注意：SearchResultsWrapper 的构造函数可能因版本而异，这里使用反射创建
                    SearchResultsWrapper wrapper;
                    try {
                        // 尝试使用 SearchResults 参数的构造函数
                        java.lang.reflect.Constructor<SearchResultsWrapper> constructor = 
                            SearchResultsWrapper.class.getConstructor(io.milvus.grpc.SearchResults.class);
                        wrapper = constructor.newInstance((io.milvus.grpc.SearchResults) resultsObj);
                    } catch (Exception e1) {
                        // 如果失败，尝试使用 Object 参数的构造函数
                        try {
                            java.lang.reflect.Constructor<SearchResultsWrapper> constructor = 
                                SearchResultsWrapper.class.getConstructor(Object.class);
                            wrapper = constructor.newInstance(resultsObj);
                        } catch (Exception e2) {
                            throw new RuntimeException("无法创建 SearchResultsWrapper，请检查 Milvus Java SDK 版本。错误: " + e2.getMessage(), e2);
                        }
                    }
                    
                    int rowCount = wrapper.getIDScore(0).size();
                    for (int i = 0; i < rowCount; i++) {
                        SearchResult result = new SearchResult();
                        
                        // 获取相似度分数（COSINE距离转换为相似度）
                        float distance = wrapper.getIDScore(0).get(i).getScore();
                        double score = 1.0 - distance;
                        result.setScore(score);
                        
                        // 从结果中获取字段值 - 使用正确的 API
                        try {
                            List<?> textList = (List<?>) wrapper.getFieldWrapper("text").getFieldData();
                            if (textList != null && i < textList.size()) {
                                result.setText(textList.get(i).toString());
                            }
                        } catch (Exception e) {
                            logger.debug("获取text字段失败", e);
                        }
                        
                        try {
                            List<?> docIdList = (List<?>) wrapper.getFieldWrapper("document_id").getFieldData();
                            if (docIdList != null && i < docIdList.size()) {
                                result.setDocumentId(((Number) docIdList.get(i)).longValue());
                            }
                        } catch (Exception e) {
                            logger.debug("获取document_id字段失败", e);
                        }
                        
                        try {
                            List<?> chunkIndexList = (List<?>) wrapper.getFieldWrapper("chunk_index").getFieldData();
                            if (chunkIndexList != null && i < chunkIndexList.size()) {
                                result.setChunkIndex(((Number) chunkIndexList.get(i)).intValue());
                            }
                        } catch (Exception e) {
                            logger.debug("获取chunk_index字段失败", e);
                        }
                        
                        results.add(result);
                    }
                } catch (Exception e) {
                    logger.error("解析搜索结果失败", e);
                    throw new RuntimeException("解析搜索结果失败: " + e.getMessage(), e);
                }
            }
            
            logger.debug("向量检索完成 - 知识库ID: {}, topK: {}, 结果数量: {}", 
                    knowledgeBaseId, topK, results.size());
            
            return results;
            
        } catch (Exception e) {
            logger.error("向量检索失败 - 知识库ID: {}, 集合名: {}", knowledgeBaseId, collectionName, e);
            throw new RuntimeException("向量检索失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 删除文档的所有向量
     */
    public void deleteDocumentVectors(Long knowledgeBaseId, Long documentId) {
        String collectionName = getCollectionName(knowledgeBaseId);
        
        // 检查集合是否存在
        if (!collectionExists(knowledgeBaseId, collectionName)) {
            logger.info("集合不存在，跳过删除操作 - 知识库ID: {}, 文档ID: {}, 集合名: {}", 
                    knowledgeBaseId, documentId, collectionName);
            return;
        }
        
        try {
            MilvusServiceClient client = getMilvusClient(knowledgeBaseId);
            
            // 构建删除表达式
            String expr = String.format("document_id == %d", documentId);
            
            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .build();
            
            logger.debug("发送删除Milvus向量请求 - 知识库ID: {}, 文档ID: {}, 集合名: {}", 
                    knowledgeBaseId, documentId, collectionName);
            
            R<?> response = client.delete(deleteParam);
            if (response.getStatus() != R.Status.Success.getCode()) {
                // 如果是集合不存在或其他错误，记录警告但不抛出异常
                logger.warn("删除Milvus向量失败 - 知识库ID: {}, 文档ID: {}, 集合名: {}, 错误: {}", 
                        knowledgeBaseId, documentId, collectionName, response.getMessage());
            } else {
                logger.info("删除文档向量成功 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId);
            }
            
        } catch (Exception e) {
            logger.warn("删除文档向量失败 - 知识库ID: {}, 文档ID: {}", knowledgeBaseId, documentId, e);
            // 删除失败不抛出异常，避免影响其他操作
        }
    }
    
    /**
     * 获取集合名称
     */
    private String getCollectionName(Long knowledgeBaseId) {
        return "kb_" + knowledgeBaseId;
    }
    
    /**
     * 生成ID
     */
    private long generateId(Long knowledgeBaseId, Long documentId, int chunkIndex) {
        // 使用简单的哈希组合，确保唯一性
        return (knowledgeBaseId * 1000000000L + documentId * 10000L + chunkIndex);
    }
    
    /**
     * 检索结果
     */
    public static class SearchResult {
        private double score;
        private String text;
        private Long documentId;
        private Integer chunkIndex;
        
        public double getScore() {
            return score;
        }
        
        public void setScore(double score) {
            this.score = score;
        }
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public Long getDocumentId() {
            return documentId;
        }
        
        public void setDocumentId(Long documentId) {
            this.documentId = documentId;
        }
        
        public Integer getChunkIndex() {
            return chunkIndex;
        }
        
        public void setChunkIndex(Integer chunkIndex) {
            this.chunkIndex = chunkIndex;
        }
    }
}
