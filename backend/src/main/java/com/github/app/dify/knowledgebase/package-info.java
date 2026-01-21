/**
 * 知识库功能模块
 * 
 * <p>本包提供了完整的知识库管理功能，包括：
 * <ul>
 *   <li>知识库和文档的CRUD操作</li>
 *   <li>智能文档分块和向量化</li>
 *   <li>基于RAG的智能问答</li>
 *   <li>多种向量数据库支持</li>
 * </ul>
 * 
 * <p>包结构说明：
 * <ul>
 *   <li>{@code controller} - REST API控制器层，提供知识库、文档、问答等接口</li>
 *   <li>{@code domain} - 实体类（知识库、文档、模型、向量库等）</li>
 *   <li>{@code repository} - 数据访问层，JPA仓库接口</li>
 *   <li>{@code service} - 业务逻辑层
 *     <ul>
 *       <li>{@code chunking} - 智能分块策略系统，根据文件类型自动选择分块方式</li>
 *       <li>{@code strategy} - 向量库策略模式实现，支持多种向量数据库</li>
 *       <li>{@code impl} - 服务实现类，包含核心业务逻辑</li>
 *     </ul>
 *   </li>
 *   <li>{@code langchain4j} - LangChain4j框架集成，提供文档处理、向量存储等能力</li>
 *   <li>{@code req} - 请求对象，定义API请求参数</li>
 *   <li>{@code resp} - 响应对象，定义API响应数据</li>
 *   <li>{@code util} - 工具类，提供转换、日期处理等辅助功能</li>
 * </ul>
 * 
 * <p>核心功能流程：
 * <ol>
 *   <li>文档上传：通过controller接收文件，存储到MinIO</li>
 *   <li>文档解析：使用TikaDocumentLoader解析文档内容</li>
 *   <li>智能分块：根据文件类型和内容特征选择合适的分块策略</li>
 *   <li>向量化：将文档块转换为向量并存储到向量数据库</li>
 *   <li>智能问答：基于RAG检索相关文档块，结合LLM生成答案</li>
 * </ol>
 * 
 * @author DifyApp Team
 * @since 1.0
 */
package com.github.app.dify.knowledgebase;
