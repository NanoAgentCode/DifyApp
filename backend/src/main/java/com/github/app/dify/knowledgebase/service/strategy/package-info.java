/**
 * 向量库策略模式实现
 * 
 * <p>本包实现了向量库访问的策略模式，支持多种向量数据库的统一抽象。
 * 通过策略模式，系统可以灵活地切换不同的向量数据库，而无需修改业务逻辑代码。
 * 
 * <p>核心接口：
 * <ul>
 *   <li>{@link VectorStoreStrategy} - 向量库策略接口，定义向量库操作的规范
 *     <ul>
 *       <li>创建向量库连接</li>
 *       <li>创建EmbeddingStore实例</li>
 *       <li>验证向量库配置</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <p>支持的向量库实现：
 * <ul>
 *   <li>{@code QdrantVectorStoreStrategy} - Qdrant向量库策略
 *     <ul>
 *       <li>Qdrant是一个开源的向量搜索引擎</li>
 *       <li>支持分布式部署和高性能检索</li>
 *     </ul>
 *   </li>
 *   <li>{@code FaissVectorStoreStrategy} - FAISS向量库策略
 *     <ul>
 *       <li>Facebook AI Similarity Search，高性能向量相似度搜索</li>
 *       <li>支持多种索引算法</li>
 *     </ul>
 *   </li>
 *   <li>{@code MilvusVectorStoreStrategy} - Milvus向量库策略
 *     <ul>
 *       <li>开源的向量数据库</li>
 *       <li>支持大规模向量存储和检索</li>
 *     </ul>
 *   </li>
 *   <li>{@code ChromaVectorStoreStrategy} - Chroma向量库策略
 *     <ul>
 *       <li>轻量级向量数据库</li>
 *       <li>易于部署和使用</li>
 *     </ul>
 *   </li>
 *   <li>{@code WeaviateVectorStoreStrategy} - Weaviate向量库策略
 *     <ul>
 *       <li>开源的向量搜索引擎</li>
 *       <li>支持GraphQL查询</li>
 *     </ul>
 *   </li>
 *   <li>{@code PgVectorVectorStoreStrategy} - PgVector向量库策略
 *     <ul>
 *       <li>基于PostgreSQL的向量扩展</li>
 *       <li>利用PostgreSQL的成熟生态</li>
 *     </ul>
 *   </li>
 *   <li>{@code ElasticsearchVectorStoreStrategy} - Elasticsearch向量库策略
 *     <ul>
 *       <li>基于Elasticsearch的向量搜索</li>
 *       <li>支持全文搜索和向量搜索结合</li>
 *     </ul>
 *   </li>
 * </ul>
 * 
 * <p>策略选择机制：
 * 系统根据向量库配置中的类型字段自动选择对应的策略实现。
 * 策略实现类通过Spring的@Component注解注册，系统会自动发现并注入。
 * 
 * <p>扩展方式：
 * 要实现新的向量库支持，需要：
 * <ol>
 *   <li>实现{@link VectorStoreStrategy}接口</li>
 *   <li>使用{@code @Component}注解注册为Spring Bean</li>
 *   <li>在{@code supports(String type)}方法中返回该向量库类型是否支持</li>
 *   <li>实现{@code createEmbeddingStore(...)}方法创建向量存储实例</li>
 * </ol>
 * 
 * <p>与langchain4j包的关系：
 * 本包的策略实现会创建langchain4j.store包中的EmbeddingStore实现类，
 * 这些实现类封装了与具体向量数据库的交互细节。
 * 
 * @author DifyApp Team
 * @since 1.0
 */
package com.github.app.dify.knowledgebase.service.strategy;
