/**
 * LangChain4j框架集成
 * 
 * <p>本包提供了与LangChain4j框架的集成，用于知识库功能的文档处理和向量存储。
 * LangChain4j是一个Java版本的LangChain框架，提供了文档处理、向量化、向量存储等能力。
 * 
 * <p>核心组件：
 * <ul>
 *   <li>{@link ConfigurableDocumentSplitter} - 可配置的文档分割器，集成智能分块策略系统</li>
 *   <li>{@link TikaDocumentLoader} - 基于Apache Tika的文档加载器，支持多种文档格式解析</li>
 *   <li>{@link VectorStoreFactory} - 向量存储工厂，创建和管理向量数据库连接</li>
 *   <li>{@link CustomEmbeddingModel} - 自定义嵌入模型，封装向量化服务</li>
 *   <li>{@link ChatLanguageModel} - 聊天语言模型封装，用于RAG问答</li>
 *   <li>{@link StreamingChatLanguageModel} - 流式聊天语言模型，支持流式响应</li>
 *   <li>{@link ModelLanguageModelFactory} - 语言模型工厂，创建和管理LLM模型实例</li>
 *   <li>{@link LangChain4jConfig} - LangChain4j配置类</li>
 * </ul>
 * 
 * <p>向量存储实现（store子包）：
 * <ul>
 *   <li>{@code BaseEmbeddingStore} - 向量存储基类</li>
 *   <li>{@code QdrantEmbeddingStore} - Qdrant向量库实现</li>
 *   <li>{@code FaissEmbeddingStore} - FAISS向量库实现</li>
 *   <li>{@code MilvusEmbeddingStore} - Milvus向量库实现</li>
 *   <li>{@code ChromaEmbeddingStore} - Chroma向量库实现</li>
 *   <li>{@code WeaviateEmbeddingStore} - Weaviate向量库实现</li>
 *   <li>{@code PgVectorEmbeddingStore} - PgVector向量库实现</li>
 *   <li>{@code EmbeddingStoreUtils} - 向量存储工具类</li>
 * </ul>
 * 
 * <p>职责：
 * <ul>
 *   <li>文档加载和解析：使用Apache Tika解析各种文档格式</li>
 *   <li>文档分块：通过ConfigurableDocumentSplitter集成智能分块策略</li>
 *   <li>向量化：将文档块转换为向量表示</li>
 *   <li>向量存储抽象：提供统一的向量数据库访问接口</li>
 *   <li>LLM模型集成：封装大语言模型的调用</li>
 * </ul>
 * 
 * <p>与知识库功能的关系：
 * <ul>
 *   <li>文档向量化流程：TikaDocumentLoader → ConfigurableDocumentSplitter → CustomEmbeddingModel → VectorStoreFactory</li>
 *   <li>RAG检索流程：VectorStoreFactory → EmbeddingStore → 向量检索</li>
 *   <li>智能问答流程：RAG检索 + ChatLanguageModel → 生成答案</li>
 * </ul>
 * 
 * <p>设计模式：
 * <ul>
 *   <li>工厂模式：VectorStoreFactory、ModelLanguageModelFactory</li>
 *   <li>策略模式：不同的向量存储实现（通过service.strategy包）</li>
 *   <li>适配器模式：将系统内部的向量化服务适配为LangChain4j的EmbeddingModel接口</li>
 * </ul>
 * 
 * @author DifyApp Team
 * @since 1.0
 */
package com.github.app.dify.knowledgebase.langchain4j;
