/**
 * 智能文档分块策略系统
 * 
 * <p>本包实现了根据文件类型和内容特征自动选择分块策略的智能分块系统。
 * 系统支持多种文件类型（Markdown、Word、PDF、代码文件等）和内容类型（文本、表格、代码、标题等），
 * 能够自动检测文档内容特征并选择最合适的分块策略。
 * 
 * <p>核心组件：
 * <ul>
 *   <li>{@link ChunkStrategySelector} - 策略选择器，根据文件类型和内容特征选择合适的分块策略</li>
 *   <li>{@link ChunkStrategy} - 分块策略接口，定义了分块操作的规范</li>
 *   <li>{@link ContentAnalyzer} - 内容分析器，分析文档内容结构（标题、表格、代码块等）</li>
 *   <li>{@link MixedContentChunker} - 混合内容分块器，处理包含多种内容类型的文档</li>
 *   <li>{@link ChunkConfig} - 分块配置，包含chunkSize和chunkOverlap等参数</li>
 *   <li>{@link ContentStructure} - 内容结构模型，描述文档的结构化信息</li>
 * </ul>
 * 
 * <p>支持的分块策略（impl包）：
 * <ul>
 *   <li>{@code FixedSizeChunkStrategy} - 固定大小分块，适用于通用文本</li>
 *   <li>{@code ParagraphChunkStrategy} - 段落分块，按段落分割，自动检测表格</li>
 *   <li>{@code SentenceChunkStrategy} - 句子分块，按句子分割</li>
 *   <li>{@code HeadingChunkStrategy} - 标题分块，按标题层级分割</li>
 *   <li>{@code CodeChunkStrategy} - 代码分块，保持代码完整性</li>
 *   <li>{@code TableChunkStrategy} - 表格分块，按表格单元分割</li>
 *   <li>{@code RecursiveChunkStrategy} - 递归分块，多层级分块策略</li>
 * </ul>
 * 
 * <p>策略选择逻辑：
 * <ul>
 *   <li>代码文件（.java, .py, .js等）→ CodeChunkStrategy</li>
 *   <li>表格文件（.csv, .xlsx等）→ TableChunkStrategy</li>
 *   <li>Markdown文件 → 根据内容特征选择（标题、表格、代码块等）</li>
 *   <li>Word文档 → 根据内容特征选择（段落、表格等）</li>
 *   <li>PDF文档 → 根据内容特征选择</li>
 *   <li>纯文本文件 → ParagraphChunkStrategy</li>
 *   <li>其他文件 → FixedSizeChunkStrategy（默认）</li>
 * </ul>
 * 
 * <p>使用场景：
 * <ul>
 *   <li>知识库文档向量化前的分块处理</li>
 *   <li>文档解读模块的文档分块</li>
 * </ul>
 * 
 * <p>扩展方式：
 * 实现{@link ChunkStrategy}接口并注册为Spring Bean，系统会自动识别并使用。
 * 策略选择器会通过{@link ChunkStrategy#supports(String, String)}方法判断策略是否适用。
 * 
 * <p>性能优化：
 * <ul>
 *   <li>策略选择器进行快速检测，避免不必要的完整内容分析</li>
 *   <li>使用预编译的正则表达式提升匹配效率</li>
 *   <li>使用HashSet存储代码文件扩展名，O(1)查找</li>
 * </ul>
 * 
 * @author DifyApp Team
 * @since 1.0
 */
package com.github.app.dify.knowledgebase.service.chunking;
