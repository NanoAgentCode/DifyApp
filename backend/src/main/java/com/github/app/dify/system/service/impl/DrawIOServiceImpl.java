package com.github.app.dify.system.service.impl;

import com.github.app.dify.system.domain.DrawIODiagram;
import com.github.app.dify.system.domain.DrawIOHistory;
import com.github.app.dify.knowledgebase.domain.QAModel;
import com.github.app.dify.knowledgebase.langchain4j.ModelLanguageModelFactory;
import com.github.app.dify.knowledgebase.langchain4j.ChatLanguageModel;
import com.github.app.dify.system.repository.DrawIODiagramRepository;
import com.github.app.dify.system.repository.DrawIOHistoryRepository;
import com.github.app.dify.system.req.DrawIOGenerateRequest;
import com.github.app.dify.system.req.DrawIOModifyRequest;
import com.github.app.dify.system.req.DrawIOSaveRequest;
import com.github.app.dify.system.req.DrawIOHistoryRequest;
import com.github.app.dify.system.resp.DrawIOGenerateResponse;
import com.github.app.dify.system.resp.DrawIODiagramResp;
import com.github.app.dify.system.resp.DrawIOHistoryResp;
import com.github.app.dify.system.service.DrawIOService;
import com.github.app.dify.model.service.ModelConfigService;
import com.github.app.dify.system.service.SystemConfigService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.app.dify.system.util.SystemConverterUtil;
import com.github.app.dify.system.util.SystemDateTimeUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DrawIO 服务实现
 */
@Service
public class DrawIOServiceImpl implements DrawIOService {
    
    private static final Logger logger = LoggerFactory.getLogger(DrawIOServiceImpl.class);
    
    @Autowired
    private DrawIODiagramRepository drawIODiagramRepository;
    
    @Autowired
    private DrawIOHistoryRepository drawIOHistoryRepository;
    
    @Autowired
    private ModelConfigService modelConfigService;
    
    @Autowired
    private SystemConfigService systemConfigService;
    
    @Autowired
    private ModelLanguageModelFactory modelLanguageModelFactory;
    
    @Override
    public DrawIOGenerateResponse generateDiagram(DrawIOGenerateRequest request, Long userId) {
        try {
            logger.info("生成图表请求 - 用户ID: {}, 提示: {}, 类型: {}", userId, request.getPrompt(), request.getDiagramType());
            
            // 获取模型
            QAModel qaModel = getQAModel(request.getModelId());
            
            // 构建系统提示词
            String systemPrompt = buildSystemPrompt(request.getDiagramType());
            
            // 构建用户提示词
            String userPrompt = buildUserPrompt(request.getPrompt(), request.getDiagramType());
            
            // 创建模型实例
            ChatLanguageModel chatLanguageModel = 
                    modelLanguageModelFactory.createChatLanguageModel(qaModel);
            
            // 构建消息
            List<ChatMessage> messages = List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            );
            
            // 调用LLM生成图表JSON
            Response<AiMessage> response = chatLanguageModel.generate(messages);
            String diagramJson = extractDiagramJson(response.content().text(), request.getDiagramType());
            
            logger.info("图表生成成功 - 用户ID: {}, JSON长度: {}", userId, diagramJson.length());
            
            // 构建响应
            DrawIOGenerateResponse generateResponse = new DrawIOGenerateResponse();
            generateResponse.setDiagramJson(diagramJson);
            generateResponse.setDiagramType(request.getDiagramType());
            
            return generateResponse;
            
        } catch (Exception e) {
            logger.error("生成图表失败 - 用户ID: {}, 提示: {}", userId, request.getPrompt(), e);
            throw new RuntimeException("生成图表失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public DrawIOGenerateResponse modifyDiagram(DrawIOModifyRequest request, Long userId) {
        try {
            logger.info("修改图表请求 - 用户ID: {}, 修改指令: {}", userId, request.getPrompt());
            
            // 获取模型
            QAModel qaModel = getQAModel(request.getModelId());
            
            // 构建系统提示词
            String systemPrompt = buildModifySystemPrompt();
            
            // 构建用户提示词
            String userPrompt = buildModifyUserPrompt(request.getDiagramJson(), request.getPrompt());
            
            // 创建模型实例
            ChatLanguageModel chatLanguageModel = 
                    modelLanguageModelFactory.createChatLanguageModel(qaModel);
            
            // 构建消息
            List<ChatMessage> messages = List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            );
            
            // 调用LLM修改图表JSON
            Response<AiMessage> response = chatLanguageModel.generate(messages);
            // 从现有图表代码中推断类型（通过检查代码开头）
            String diagramType = inferDiagramType(request.getDiagramJson());
            String diagramJson = extractDiagramJson(response.content().text(), diagramType);
            
            logger.info("图表修改成功 - 用户ID: {}, JSON长度: {}", userId, diagramJson.length());
            
            // 构建响应
            DrawIOGenerateResponse generateResponse = new DrawIOGenerateResponse();
            generateResponse.setDiagramJson(diagramJson);
            
            return generateResponse;
            
        } catch (Exception e) {
            logger.error("修改图表失败 - 用户ID: {}, 修改指令: {}", userId, request.getPrompt(), e);
            throw new RuntimeException("修改图表失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    @Transactional
    public DrawIODiagramResp saveDiagram(DrawIOSaveRequest request, Long userId) {
        try {
            logger.info("保存图表请求 - 用户ID: {}, 名称: {}", userId, request.getName());
            
            DrawIODiagram diagram = new DrawIODiagram();
            diagram.setName(request.getName());
            diagram.setDiagramType(request.getDiagramType());
            diagram.setDiagramJson(request.getDiagramJson());
            diagram.setUserId(userId);
            SystemDateTimeUtil.setCreateAndUpdateTime(diagram);
            diagram.setDeleted(0);
            
            diagram = drawIODiagramRepository.save(diagram);
            
            logger.info("图表保存成功 - ID: {}, 用户ID: {}", diagram.getId(), userId);
            
            return SystemConverterUtil.convertToResp(diagram);
            
        } catch (Exception e) {
            logger.error("保存图表失败 - 用户ID: {}, 名称: {}", userId, request.getName(), e);
            throw new RuntimeException("保存图表失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<DrawIODiagramResp> getDiagramList(Long userId) {
        try {
            List<DrawIODiagram> diagrams = drawIODiagramRepository.findByUserIdAndNotDeleted(userId);
            return diagrams.stream()
                    .map(this::convertToResp)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取图表列表失败 - 用户ID: {}", userId, e);
            throw new RuntimeException("获取图表列表失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public DrawIODiagramResp getDiagramDetail(Long id, Long userId) {
        try {
            DrawIODiagram diagram = drawIODiagramRepository.findByIdAndUserId(id, userId)
                    .orElseThrow(() -> new RuntimeException("图表不存在或无权限访问"));
            
            return SystemConverterUtil.convertToResp(diagram);
        } catch (Exception e) {
            logger.error("获取图表详情失败 - ID: {}, 用户ID: {}", id, userId, e);
            throw new RuntimeException("获取图表详情失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    @Transactional
    public void deleteDiagram(Long id, Long userId) {
        try {
            DrawIODiagram diagram = drawIODiagramRepository.findByIdAndUserId(id, userId)
                    .orElseThrow(() -> new RuntimeException("图表不存在或无权限访问"));
            
            diagram.setDeleted(1);
            SystemDateTimeUtil.setUpdateTime(diagram);
            drawIODiagramRepository.save(diagram);
            
            logger.info("图表删除成功 - ID: {}, 用户ID: {}", id, userId);
        } catch (Exception e) {
            logger.error("删除图表失败 - ID: {}, 用户ID: {}", id, userId, e);
            throw new RuntimeException("删除图表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取问答模型
     * 优先级：1. 请求中的modelId 2. 系统配置中的drawio.defaultModelId 3. 默认RAG模型
     */
    private QAModel getQAModel(Long modelId) {
        try {
            QAModel qaModel;
            if (modelId != null) {
                // 使用请求中指定的模型
                qaModel = modelConfigService.getQAModelById(modelId);
                if (qaModel == null) {
                    throw new IllegalStateException("指定的模型不存在，ID: " + modelId);
                }
            } else {
                // 尝试从系统配置读取AI绘图默认模型
                String defaultModelIdStr = systemConfigService.getConfigValue("drawio.defaultModelId");
                if (defaultModelIdStr != null && !defaultModelIdStr.trim().isEmpty()) {
                    try {
                        Long defaultModelId = Long.parseLong(defaultModelIdStr.trim());
                        qaModel = modelConfigService.getQAModelById(defaultModelId);
                        if (qaModel != null) {
                            logger.info("使用系统配置的AI绘图默认模型: {} (ID: {})", qaModel.getName(), qaModel.getId());
                        } else {
                            logger.warn("系统配置的AI绘图默认模型不存在，ID: {}，将使用默认RAG模型", defaultModelId);
                            qaModel = modelConfigService.getDefaultQAModelForRAG();
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("系统配置的AI绘图默认模型ID格式错误: {}，将使用默认RAG模型", defaultModelIdStr);
                        qaModel = modelConfigService.getDefaultQAModelForRAG();
                    }
                } else {
                    // 使用默认RAG模型
                    qaModel = modelConfigService.getDefaultQAModelForRAG();
                }
                
                if (qaModel == null) {
                    throw new IllegalStateException("未找到可用的问答模型，请先在系统配置中配置模型或设置drawio.defaultModelId");
                }
            }
            logger.info("使用问答模型: {} (ID: {})", qaModel.getName(), qaModel.getId());
            return qaModel;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            logger.error("获取问答模型失败 - modelId: {}", modelId, e);
            throw new IllegalStateException("获取问答模型失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 构建系统提示词（生成）
     */
    private String buildSystemPrompt(String diagramType) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的图表生成助手，专门生成 Mermaid 格式的图表代码。\n\n");
        prompt.append("重要要求：\n");
        prompt.append("1. 你必须只返回有效的 Mermaid 代码，不要包含任何解释文字\n");
        prompt.append("2. 代码必须符合 Mermaid 语法规范\n");
        prompt.append("3. 使用中文标签和文本\n");
        prompt.append("4. 代码块不要使用 ```mermaid 包裹，直接返回代码\n");
        prompt.append("5. 必须使用指定的颜色主题来区分不同类型的组件\n");
        prompt.append("6. **关键要求：必须生成详细、完整的图表，包含足够的节点和层次**\n");
        prompt.append("   - 流程图：至少包含 8-15 个节点，覆盖所有主要步骤和分支\n");
        prompt.append("   - 架构图：至少包含 3-5 层，每层至少 3-5 个组件\n");
        prompt.append("   - 思维导图：至少包含 3-4 个主要分支，每个分支至少 2-3 个子分支\n");
        prompt.append("   - 时序图：至少包含 4-6 个参与者，10-15 条交互消息\n");
        prompt.append("   - UML图：至少包含 5-8 个类，展示完整的关系\n");
        prompt.append("   - 组织架构：至少包含 3-4 层，每层多个节点\n");
        prompt.append("   - 网络图：至少包含 8-12 个设备节点\n");
        prompt.append("7. 必须充分理解用户描述，生成包含所有相关细节的完整图表，不要简化或省略任何重要部分\n\n");
        
        // 添加颜色主题约束
        prompt.append("颜色主题规范（必须严格遵守）：\n");
        prompt.append("- 浅蓝色 (#ADD8E6 或 #E3F2FD): 用于输入/输出嵌入层、线性层、基础组件\n");
        prompt.append("- 黄色 (#FFD700 或 #FFF9C4): 用于位置编码、时间相关组件\n");
        prompt.append("- 紫色 (#9370DB 或 #BA68C8): 用于编码器块、编码相关组件\n");
        prompt.append("- 红色 (#FF5252 或 #F44336): 用于注意力机制、前馈网络、核心处理组件\n");
        prompt.append("- 绿色 (#4CAF50 或 #66BB6A): 用于归一化层、添加操作、辅助组件\n");
        prompt.append("- 橙色 (#FF9800 或 #FFB74D): 用于解码器块、解码相关组件\n");
        prompt.append("- 深蓝色 (#1976D2 或 #1565C0): 用于输出层、最终结果\n");
        prompt.append("- 灰色 (#808080 或 #9E9E9E): 用于连接线、辅助连接\n\n");
        
        prompt.append("在 Mermaid 中使用颜色的方法（注意：不同图表类型支持不同）：\n");
        prompt.append("1. 对于 flowchart：使用 classDef 定义样式类，然后使用 class 语句或 ::: 语法\n");
        prompt.append("   例如：classDef lightBlue fill:#ADD8E6,stroke:#1976D2,stroke-width:2px\n");
        prompt.append("   然后：class A lightBlue 或 A[节点]:::lightBlue\n");
        prompt.append("2. 对于 mindmap：不支持自定义颜色类，mindmap 会自动应用颜色，不要使用 ::: 或 classDef\n");
        prompt.append("   mindmap 语法：mindmap\\n  root((中心))\\n    分支1\\n      子分支\n");
        prompt.append("3. 对于 sequenceDiagram：不支持颜色类，使用 participant 定义即可\n");
        prompt.append("4. 对于 classDiagram：不支持 classDef，必须使用 style 语句，例如：style ClassName fill:#ADD8E6,stroke:#1976D2,stroke-width:2px\n");
        prompt.append("5. 确保不同类型的组件使用对应的颜色，保持视觉一致性\n\n");
        
        // 根据图表类型添加特定指导
        if (diagramType != null) {
            switch (diagramType) {
                case "flowchart":
                    prompt.append("流程图要求：\n");
                    prompt.append("- 使用 flowchart TD 或 flowchart LR 开始\n");
                    prompt.append("- 使用方括号 [] 表示矩形节点\n");
                    prompt.append("- 使用圆括号 () 表示圆角矩形（浅蓝色）\n");
                    prompt.append("- 使用花括号 {} 表示菱形判断节点（黄色）\n");
                    prompt.append("- 使用 --> 或 ---> 连接节点\n");
                    prompt.append("- **必须生成详细的流程图，至少包含 8-15 个节点，覆盖所有步骤、判断、循环、异常处理等**\n");
                    prompt.append("- 必须使用颜色区分节点类型：\n");
                    prompt.append("  * 浅蓝色：开始/结束节点\n");
                    prompt.append("  * 黄色：判断节点\n");
                    prompt.append("  * 红色：处理节点、核心步骤\n");
                    prompt.append("  * 绿色：辅助步骤、验证节点\n");
                    prompt.append("- 重要：classDef 名称不能使用保留关键字（如：start, end, default, classDef, class, linkStyle, style 等）\n");
                    prompt.append("- 使用安全的 classDef 名称，例如：startNode, endNode, decisionNode, processNode, helperNode\n");
                    prompt.append("正确示例：flowchart TD\n");
                    prompt.append("    classDef startNode fill:#ADD8E6,stroke:#1976D2,stroke-width:2px\n");
                    prompt.append("    classDef decisionNode fill:#FFD700,stroke:#F57F17,stroke-width:2px\n");
                    prompt.append("    classDef processNode fill:#FF5252,stroke:#C62828,stroke-width:2px\n");
                    prompt.append("    A[开始]:::startNode --> B{判断}:::decisionNode\n");
                    prompt.append("    B -->|是| C[处理]:::processNode\n");
                    prompt.append("    B -->|否| D[结束]:::startNode\n");
                    prompt.append("错误示例（不要使用）：\n");
                    prompt.append("    classDef start fill:...  ❌ 错误，start 是保留关键字\n");
                    prompt.append("    classDef end fill:...  ❌ 错误，end 是保留关键字\n");
                    prompt.append("    classDef default fill:...  ❌ 错误，default 是保留关键字\n");
                    break;
                case "architecture":
                    prompt.append("架构图要求（重要：整体垂直分层，每层内部水平排列，无连接线）：\n");
                    prompt.append("- **必须使用 flowchart TD（从上到下）**，确保层与层之间垂直排列\n");
                    prompt.append("- **使用 subgraph 定义不同的层**，每层是一个独立的 subgraph\n");
                    prompt.append("- 层的顺序很重要：从上到下依次排列，第一层在最上面，最后一层在最下面\n");
                    prompt.append("- **重要**：每个 subgraph 内使用 `direction LR` 来强制水平排列\n");
                    prompt.append("- 在每层内使用方括号 [] 定义组件，组件水平排列\n");
                    prompt.append("- **绝对禁止**：不要使用任何连接线（`-->`、`---`、`-.->` 等），架构图不需要连接线\n");
                    prompt.append("- 如果组件太多，可以自动换行（Mermaid 会自动处理）\n");
                    prompt.append("- 层与层之间不使用任何连接线，只通过垂直排列（从上到下）表示分层关系\n");
                    prompt.append("- **必须生成详细的架构图，至少包含 3-5 层，每层至少 3-5 个组件，总共至少 12-20 个组件**\n");
                    prompt.append("- 必须使用颜色主题区分不同类型的组件：\n");
                    prompt.append("  * 浅蓝色 (#ADD8E6)：表示层、前端、网关、接口层\n");
                    prompt.append("  * 紫色 (#9370DB)：服务层、业务服务、中间件\n");
                    prompt.append("  * 红色 (#FF5252)：核心业务层、计算层、处理层\n");
                    prompt.append("  * 绿色 (#4CAF50)：工具层、辅助服务、工具服务\n");
                    prompt.append("  * 深蓝色 (#1976D2)：数据层、存储层、数据库\n");
                    prompt.append("  * 橙色 (#FF9800)：输出层、展示层、结果层\n");
                    prompt.append("正确示例（整体垂直分层，每层内部水平排列，无连接线）：\n");
                    prompt.append("flowchart TD\n");
                    prompt.append("    classDef presentation fill:#ADD8E6,stroke:#1976D2,stroke-width:2px\n");
                    prompt.append("    classDef service fill:#9370DB,stroke:#7B1FA2,stroke-width:2px\n");
                    prompt.append("    classDef core fill:#FF5252,stroke:#C62828,stroke-width:2px\n");
                    prompt.append("    classDef tool fill:#4CAF50,stroke:#2E7D32,stroke-width:2px\n");
                    prompt.append("    classDef data fill:#1976D2,stroke:#0D47A1,stroke-width:2px\n");
                    prompt.append("    subgraph Layer1[\"表示层\"]\n");
                    prompt.append("        direction LR\n");
                    prompt.append("        A[Web前端]:::presentation\n");
                    prompt.append("        B[移动端]:::presentation\n");
                    prompt.append("        C[API网关]:::presentation\n");
                    prompt.append("    end\n");
                    prompt.append("    subgraph Layer2[\"服务层\"]\n");
                    prompt.append("        direction LR\n");
                    prompt.append("        D[用户服务]:::service\n");
                    prompt.append("        E[订单服务]:::service\n");
                    prompt.append("        F[支付服务]:::service\n");
                    prompt.append("    end\n");
                    prompt.append("    subgraph Layer3[\"核心业务层\"]\n");
                    prompt.append("        direction LR\n");
                    prompt.append("        G[业务逻辑]:::core\n");
                    prompt.append("        H[计算引擎]:::core\n");
                    prompt.append("        I[处理服务]:::core\n");
                    prompt.append("    end\n");
                    prompt.append("    subgraph Layer4[\"工具层\"]\n");
                    prompt.append("        direction LR\n");
                    prompt.append("        J[日志服务]:::tool\n");
                    prompt.append("        K[监控服务]:::tool\n");
                    prompt.append("        L[缓存服务]:::tool\n");
                    prompt.append("    end\n");
                    prompt.append("    subgraph Layer5[\"数据层\"]\n");
                    prompt.append("        direction LR\n");
                    prompt.append("        M[MySQL]:::data\n");
                    prompt.append("        N[Redis]:::data\n");
                    prompt.append("        O[MongoDB]:::data\n");
                    prompt.append("    end\n");
                    prompt.append("重要说明：\n");
                    prompt.append("- 每个 subgraph 内必须使用 `direction LR` 来强制水平排列\n");
                    prompt.append("- **绝对不要使用任何连接线**（`-->`、`---`、`-.->` 等），架构图不需要连接线\n");
                    prompt.append("- 如果组件太多，Mermaid 会自动换行\n");
                    prompt.append("- 层与层之间不使用任何连接线\n");
                    prompt.append("错误示例（绝对不要使用）：\n");
                    prompt.append("    flowchart LR  ❌ 错误，必须使用 TD（整体从上到下）\n");
                    prompt.append("    A --> B  ❌ 错误，架构图不需要连接线\n");
                    prompt.append("    Layer1 --> Layer2  ❌ 错误，层之间不连接\n");
                    prompt.append("记住：架构图整体垂直分层（TD），每层内部水平排列（LR），不使用任何连接线！\n");
                    break;
                case "mindmap":
                    prompt.append("思维导图要求（严格遵守）：\n");
                    prompt.append("- 使用 mindmap 开始\n");
                    prompt.append("- 使用缩进表示层级关系（使用2个空格缩进）\n");
                    prompt.append("- 中心节点使用 root((文本)) 格式，双圆括号\n");
                    prompt.append("- 分支节点直接使用文本，不需要括号，不需要任何样式标记\n");
                    prompt.append("- **必须生成详细的思维导图，至少包含 3-4 个主要分支，每个主要分支至少 2-3 个子分支，总共至少 15-25 个节点**\n");
                    prompt.append("- 绝对禁止：不要在任何节点后添加 :::样式名，mindmap 不支持这种语法\n");
                    prompt.append("- 绝对禁止：不要使用 classDef 或 class 语句\n");
                    prompt.append("- mindmap 会自动应用颜色，无需手动指定任何颜色或样式\n");
                    prompt.append("- 每行只能包含节点文本，不能有任何其他标记\n");
                    prompt.append("正确示例（必须严格按照此格式）：\n");
                    prompt.append("mindmap\n");
                    prompt.append("  root((中心主题))\n");
                    prompt.append("    主要分支1\n");
                    prompt.append("      次要分支1\n");
                    prompt.append("      次要分支2\n");
                    prompt.append("    主要分支2\n");
                    prompt.append("      次要分支3\n");
                    prompt.append("错误示例（绝对不要使用）：\n");
                    prompt.append("mindmap\n");
                    prompt.append("  root((中心主题)):::deepBlue  ❌ 错误，不要使用 :::\n");
                    prompt.append("    分支:::purple  ❌ 错误，不要使用 :::\n");
                    prompt.append("    分支:::lightBlue  ❌ 错误，不要使用 :::\n");
                    prompt.append("记住：mindmap 中每一行只能是节点文本，不能有任何样式标记！\n");
                    break;
                case "sequence":
                    prompt.append("时序图要求：\n");
                    prompt.append("- 使用 sequenceDiagram 开始\n");
                    prompt.append("- 使用 participant 定义参与者\n");
                    prompt.append("- 使用 -> 或 --> 表示消息\n");
                    prompt.append("- **必须生成详细的时序图，至少包含 4-6 个参与者，10-15 条交互消息，覆盖完整的交互流程**\n");
                    prompt.append("- 不同类型的参与者使用不同颜色：客户端(浅蓝色)、服务端(紫色)、数据库(深蓝色)\n");
                    prompt.append("示例：sequenceDiagram\n    participant C as 客户端\n    participant S as 服务端\n    participant D as 数据库\n    C->>S: 请求\n    S->>D: 查询\n");
                    break;
                case "uml":
                    prompt.append("UML图要求：\n");
                    prompt.append("- 使用 classDiagram 开始\n");
                    prompt.append("- 使用 class 定义类，例如：class ClassName\n");
                    prompt.append("- 使用 --> 或 <|-- 表示关系\n");
                    prompt.append("- 重要：classDiagram 不支持 classDef 语法，必须使用 style 语句来应用样式\n");
                    prompt.append("- 样式语法：style ClassName fill:#颜色,stroke:#颜色,stroke-width:2px\n");
                    prompt.append("- 实体类使用浅蓝色，服务类使用紫色，接口使用绿色，实现类使用橙色\n");
                    prompt.append("正确示例：\n");
                    prompt.append("classDiagram\n");
                    prompt.append("    class User\n");
                    prompt.append("    class Order\n");
                    prompt.append("    class Product\n");
                    prompt.append("    User --> Order\n");
                    prompt.append("    Order --> Product\n");
                    prompt.append("    style User fill:#ADD8E6,stroke:#1976D2,stroke-width:2px\n");
                    prompt.append("    style Order fill:#9370DB,stroke:#7B1FA2,stroke-width:2px\n");
                    prompt.append("    style Product fill:#4CAF50,stroke:#2E7D32,stroke-width:2px\n");
                    prompt.append("错误示例（不要使用）：\n");
                    prompt.append("    classDef lightBlue fill:...  ❌ 错误，classDiagram 不支持 classDef\n");
                    prompt.append("    class User:::lightBlue  ❌ 错误，classDiagram 不支持 ::: 语法\n");
                    break;
                case "org":
                    prompt.append("组织架构图要求：\n");
                    prompt.append("- 使用 flowchart TD 开始\n");
                    prompt.append("- 使用方括号 [] 表示职位\n");
                    prompt.append("- 使用 --> 表示上下级关系\n");
                    prompt.append("- **必须生成详细的组织架构图，至少包含 3-4 层，每层多个节点，总共至少 12-20 个节点**\n");
                    prompt.append("- 高层管理使用深蓝色，中层管理使用紫色，基层员工使用浅蓝色\n");
                    prompt.append("示例：flowchart TD\n");
                    prompt.append("    classDef executive fill:#1976D2,stroke:#0D47A1,stroke-width:2px\n");
                    prompt.append("    classDef manager fill:#9370DB,stroke:#7B1FA2,stroke-width:2px\n");
                    prompt.append("    classDef employee fill:#ADD8E6,stroke:#1976D2,stroke-width:2px\n");
                    prompt.append("    A[CEO]:::executive --> B[部门经理]:::manager\n");
                    prompt.append("    B --> C[员工]:::employee\n");
                    break;
                case "network":
                    prompt.append("网络图要求：\n");
                    prompt.append("- 使用 flowchart LR 开始\n");
                    prompt.append("- 使用方括号 [] 表示设备\n");
                    prompt.append("- 使用 --> 表示连接\n");
                    prompt.append("- **必须生成详细的网络图，至少包含 8-12 个设备节点，展示完整的网络拓扑结构**\n");
                    prompt.append("- 核心设备使用红色，网络设备使用紫色，终端设备使用浅蓝色，服务器使用橙色\n");
                    prompt.append("示例：flowchart LR\n");
                    prompt.append("    classDef core fill:#FF5252,stroke:#C62828,stroke-width:2px\n");
                    prompt.append("    classDef network fill:#9370DB,stroke:#7B1FA2,stroke-width:2px\n");
                    prompt.append("    classDef terminal fill:#ADD8E6,stroke:#1976D2,stroke-width:2px\n");
                    prompt.append("    classDef server fill:#FF9800,stroke:#E65100,stroke-width:2px\n");
                    prompt.append("    A[核心交换机]:::core --> B[路由器]:::network\n");
                    prompt.append("    B --> C[服务器]:::server\n");
                    prompt.append("    B --> D[终端]:::terminal\n");
                    break;
            }
        }
        
        prompt.append("\n请直接返回完整的 Mermaid 代码，不要包含任何其他文字说明，不要使用代码块包裹。");
        
        return prompt.toString();
    }
    
    /**
     * 构建用户提示词（生成）
     */
    private String buildUserPrompt(String userPrompt, String diagramType) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下描述生成详细、完整的 Mermaid 格式图表代码：\n\n");
        prompt.append(userPrompt);
        prompt.append("\n\n");
        prompt.append("重要要求：\n");
        prompt.append("1. 必须生成详细、完整的图表，包含所有相关节点和细节\n");
        prompt.append("2. 不要简化或省略任何重要部分，要充分展开用户描述的所有内容\n");
        
        // 根据图表类型添加具体要求
        if (diagramType != null) {
            switch (diagramType) {
                case "flowchart":
                    prompt.append("3. 流程图必须包含所有步骤、判断分支、循环等完整流程，至少 8-15 个节点\n");
                    break;
                case "architecture":
                    prompt.append("3. 架构图必须包含所有层级和组件，每层至少 3-5 个组件，至少 3-5 层\n");
                    break;
                case "mindmap":
                    prompt.append("3. 思维导图必须包含所有主要分支和子分支，至少 3-4 个主要分支，每个分支至少 2-3 个子分支\n");
                    break;
                case "sequence":
                    prompt.append("3. 时序图必须包含所有参与者和完整的交互序列，至少 4-6 个参与者，10-15 条消息\n");
                    break;
                case "uml":
                    prompt.append("3. UML图必须包含所有相关的类和关系，至少 5-8 个类\n");
                    break;
                case "org":
                    prompt.append("3. 组织架构图必须包含所有层级和人员，至少 3-4 层\n");
                    break;
                case "network":
                    prompt.append("3. 网络图必须包含所有网络设备和连接，至少 8-12 个设备节点\n");
                    break;
            }
        }
        
        prompt.append("4. 请生成完整的 Mermaid 代码，不要使用代码块包裹。");
        return prompt.toString();
    }
    
    /**
     * 构建系统提示词（修改）
     */
    private String buildModifySystemPrompt() {
        return "你是一个专业的图表修改助手，专门修改 Mermaid 格式的图表代码。\n\n" +
                "重要要求：\n" +
                "1. 你必须只返回修改后的完整 Mermaid 代码，不要包含任何解释文字\n" +
                "2. 保持原有图表的整体结构和风格\n" +
                "3. 根据修改指令进行精确修改\n" +
                "4. 确保修改后的代码仍然有效且符合 Mermaid 语法规范\n" +
                "5. 使用中文标签和文本\n" +
                "6. 不要使用代码块包裹，直接返回代码\n" +
                "7. 必须严格遵守颜色主题规范，保持颜色一致性：\n" +
                "   - 浅蓝色 (#ADD8E6): 输入/输出嵌入层、基础组件\n" +
                "   - 黄色 (#FFD700): 位置编码、时间相关组件\n" +
                "   - 紫色 (#9370DB): 编码器块、编码相关组件\n" +
                "   - 红色 (#FF5252): 注意力机制、前馈网络、核心处理组件\n" +
                "   - 绿色 (#4CAF50): 归一化层、添加操作、辅助组件\n" +
                "   - 橙色 (#FF9800): 解码器块、解码相关组件\n" +
                "   - 深蓝色 (#1976D2): 输出层、最终结果\n" +
                "   - 灰色 (#808080): 连接线、辅助连接\n\n" +
                "请直接返回完整的修改后的 Mermaid 代码，不要包含任何其他文字说明。";
    }
    
    /**
     * 构建用户提示词（修改）
     */
    private String buildModifyUserPrompt(String diagramJson, String modifyInstruction) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("现有图表代码：\n");
        prompt.append("```\n");
        // 只传递代码的关键部分，避免过长
        if (diagramJson.length() > 5000) {
            prompt.append(diagramJson.substring(0, 5000)).append("...\n");
        } else {
            prompt.append(diagramJson).append("\n");
        }
        prompt.append("```\n\n");
        prompt.append("修改指令：\n");
        prompt.append(modifyInstruction);
        prompt.append("\n\n请根据修改指令生成修改后的完整 Mermaid 代码，不要使用代码块包裹。");
        return prompt.toString();
    }
    
    /**
     * 从LLM响应中提取图表代码（Mermaid）
     */
    /**
     * 推断图表类型
     */
    private String inferDiagramType(String diagramJson) {
        if (diagramJson == null || diagramJson.isEmpty()) {
            return null;
        }
        String trimmed = diagramJson.trim();
        if (trimmed.startsWith("flowchart") || trimmed.startsWith("graph")) {
            return "flowchart";
        } else if (trimmed.startsWith("mindmap")) {
            return "mindmap";
        } else if (trimmed.startsWith("sequenceDiagram")) {
            return "sequence";
        } else if (trimmed.startsWith("classDiagram")) {
            return "uml";
        } else if (trimmed.startsWith("erDiagram")) {
            return "network";
        }
        return null;
    }
    
    private String extractDiagramJson(String response, String diagramType) {
        String extracted = null;
        
        // 尝试提取代码块
        if (response.contains("```mermaid")) {
            int start = response.indexOf("```mermaid") + 10;
            int end = response.indexOf("```", start);
            if (end > start) {
                extracted = response.substring(start, end).trim();
            }
        } else if (response.contains("```")) {
            int start = response.indexOf("```") + 3;
            int end = response.indexOf("```", start);
            if (end > start) {
                extracted = response.substring(start, end).trim();
                // 检查是否是 Mermaid 代码（通常以 flowchart, graph, sequenceDiagram, classDiagram, mindmap 等开头）
                if (!extracted.startsWith("flowchart") && !extracted.startsWith("graph") && 
                    !extracted.startsWith("sequenceDiagram") && !extracted.startsWith("classDiagram") &&
                    !extracted.startsWith("mindmap") && !extracted.startsWith("erDiagram") &&
                    !extracted.startsWith("gantt") && !extracted.startsWith("pie")) {
                    extracted = null;
                }
            }
        }
        
        // 如果都没有，检查原始响应是否已经是 Mermaid 代码
        if (extracted == null) {
            String trimmed = response.trim();
            if (trimmed.startsWith("flowchart") || trimmed.startsWith("graph") || 
                trimmed.startsWith("sequenceDiagram") || trimmed.startsWith("classDiagram") ||
                trimmed.startsWith("mindmap") || trimmed.startsWith("erDiagram") ||
                trimmed.startsWith("gantt") || trimmed.startsWith("pie")) {
                extracted = trimmed;
            } else {
                extracted = trimmed;
            }
        }
        
        // 清理 mindmap 中不支持的语法
        if (extracted != null && extracted.startsWith("mindmap")) {
            extracted = cleanMindmapCode(extracted);
        }
        
        // 清理 flowchart 中的保留关键字问题
        if (extracted != null && (extracted.startsWith("flowchart") || extracted.startsWith("graph"))) {
            extracted = cleanFlowchartCode(extracted);
        }
        
        // 清理 classDiagram 中不支持的 classDef 语法
        if (extracted != null && extracted.startsWith("classDiagram")) {
            extracted = cleanClassDiagramCode(extracted);
        }
        
        // 清理 classDiagram 中不支持的 classDef 语法
        if (extracted != null && extracted.startsWith("classDiagram")) {
            extracted = cleanClassDiagramCode(extracted);
        }
        
        // 清理架构图代码（确保整体垂直分层，每层内部水平排列）
        if (extracted != null && "architecture".equals(diagramType)) {
            // 检查是否是 flowchart 或 graph
            if (extracted.startsWith("flowchart") || extracted.startsWith("graph")) {
                extracted = cleanArchitectureCode(extracted);
            }
        }
        
        return extracted;
    }
    
    /**
     * 清理 mindmap 代码，移除不支持的 ::: 样式语法
     */
    private String cleanMindmapCode(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        
        logger.info("清理前的 mindmap 代码: {}", code);
        
        // 按行处理，逐行清理
        String[] lines = code.split("\n");
        StringBuilder result = new StringBuilder();
        
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                result.append("\n");
                continue;
            }
            
            // 保持原有的缩进
            int indent = 0;
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) == ' ' || line.charAt(i) == '\t') {
                    indent++;
                } else {
                    break;
                }
            }
            
            // 获取去除缩进后的内容
            String content = line.substring(indent);
            
            // 移除所有的 :::样式名 语法
            // 使用更全面的正则表达式匹配 ::: 后跟字母、数字、下划线的组合
            // 匹配行中任意位置的 :::样式名（包括行尾）
            content = content.replaceAll(":::\\w+", "");  // 移除所有 :::样式名
            
            // 清理多余的空格（::: 前后可能有多余空格）
            content = content.replaceAll("\\s+", " ");  // 多个连续空格变为单个空格
            
            // 清理行尾多余空格
            content = content.trim();
            
            // 重新组合行
            if (!content.isEmpty()) {
                for (int i = 0; i < indent; i++) {
                    result.append(" ");
                }
                result.append(content).append("\n");
            } else {
                result.append("\n");
            }
        }
        
        String cleaned = result.toString().trim();
        logger.info("清理后的 mindmap 代码: {}", cleaned);
        
        return cleaned;
    }
    
    /**
     * 清理 flowchart 代码，修复保留关键字问题
     */
    private String cleanFlowchartCode(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        
        logger.info("清理前的 flowchart 代码: {}", code);
        
        // Mermaid flowchart 的保留关键字（不能用作 classDef 样式名）
        // 注意：classDef 和 class 本身是指令关键字，不需要替换
        String[] reservedKeywords = {"start", "end", "default", "linkStyle", "style"};
        
        // 替换保留关键字为安全的名称
        String cleaned = code;
        for (String keyword : reservedKeywords) {
            // 1. 替换 classDef 语句中的保留关键字（作为样式名）
            // 例如：classDef start fill:... -> classDef startNode fill:...
            // 例如：classDef end fill:... -> classDef endNode fill:...
            String pattern = "classDef\\s+" + keyword + "\\b";
            String replacement = "classDef " + keyword + "Node";
            cleaned = cleaned.replaceAll(pattern, replacement);
            
            // 2. 替换使用这些关键字的 ::: 语法（在节点定义后）
            // 例如：A[开始]:::start -> A[开始]:::startNode
            // 例如：D[结束]:::end -> D[结束]:::endNode
            pattern = ":::" + keyword + "\\b";
            replacement = ":::" + keyword + "Node";
            cleaned = cleaned.replaceAll(pattern, replacement);
            
            // 3. 替换 class 语句中的保留关键字（作为样式名）
            // 例如：class A start -> class A startNode
            // 例如：class B end -> class B endNode
            pattern = "class\\s+(\\w+)\\s+" + keyword + "\\b";
            replacement = "class $1 " + keyword + "Node";
            cleaned = cleaned.replaceAll(pattern, replacement);
        }
        
        logger.info("清理后的 flowchart 代码: {}", cleaned);
        
        return cleaned;
    }
    
    /**
     * 清理 classDiagram 代码，将 classDef 转换为 style 语句
     */
    private String cleanClassDiagramCode(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        
        logger.info("清理前的 classDiagram 代码: {}", code);
        
        // 按行处理
        String[] lines = code.split("\n");
        StringBuilder result = new StringBuilder();
        Map<String, String> classDefMap = new HashMap<>();
        Map<String, String> classNameToDefMap = new HashMap<>(); // 记录哪些类使用了哪些 classDef
        
        for (String line : lines) {
            String trimmed = line.trim();
            String indent = line.substring(0, line.length() - trimmed.length());
            
            // 匹配 classDef 语句：classDef name fill:...,stroke:...
            if (trimmed.startsWith("classDef ")) {
                // 提取 classDef 名称和样式
                // 例如：classDef lightBlue fill:#ADD8E6,stroke:#1976D2,stroke-width:2px
                String pattern = "classDef\\s+(\\w+)\\s+(.+)";
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                java.util.regex.Matcher m = p.matcher(trimmed);
                if (m.find()) {
                    String defName = m.group(1);
                    String style = m.group(2);
                    // 存储 classDef 定义，稍后转换为 style
                    classDefMap.put(defName, style);
                    // 不添加到结果中（classDiagram 不支持 classDef）
                    continue;
                }
            }
            
            // 匹配使用 classDef 的 class 语句：class ClassName:::defName
            if (trimmed.startsWith("class ")) {
                // 检查是否有 ::: 语法
                if (trimmed.contains(":::")) {
                    // 例如：class User:::lightBlue
                    String pattern = "class\\s+(\\w+)\\s*:::\\s*(\\w+)";
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
                    java.util.regex.Matcher m = p.matcher(trimmed);
                    if (m.find()) {
                        String className = m.group(1);
                        String defName = m.group(2);
                        // 记录这个类使用的 classDef
                        classNameToDefMap.put(className, defName);
                        // 只添加 class 定义，不包含 ::: 部分
                        result.append(indent).append("class ").append(className).append("\n");
                        continue;
                    }
                }
                
                // 检查是否有单独的 class 应用语句：class ClassName defName（在下一行）
                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 3) {
                    // 可能是 class ClassName defName 格式
                    String className = parts[1];
                    String defName = parts[2];
                    if (classDefMap.containsKey(defName)) {
                        // 记录这个类使用的 classDef
                        classNameToDefMap.put(className, defName);
                        // 只添加 class 定义
                        result.append(indent).append("class ").append(className).append("\n");
                        continue;
                    }
                }
            }
            
            // 普通行，直接添加
            result.append(line).append("\n");
        }
        
        // 为所有使用了 classDef 的类添加 style 语句
        for (Map.Entry<String, String> entry : classNameToDefMap.entrySet()) {
            String className = entry.getKey();
            String defName = entry.getValue();
            if (classDefMap.containsKey(defName)) {
                String style = classDefMap.get(defName);
                result.append("    style ").append(className).append(" ").append(style).append("\n");
            }
        }
        
        // 如果还有未应用的 classDef，添加警告日志
        if (!classDefMap.isEmpty() && classNameToDefMap.isEmpty()) {
            logger.warn("检测到未使用的 classDef 定义，已自动移除: {}", classDefMap.keySet());
        }
        
        String cleaned = result.toString().trim();
        logger.info("清理后的 classDiagram 代码: {}", cleaned);
        
        return cleaned;
    }
    
    /**
     * 清理架构图代码，确保整体垂直分层，每层内部水平排列
     * 同时确保使用 flowchart TD（从上到下），而不是 LR（左右）
     * 确保每个 subgraph 内有 direction LR 和水平连接用于水平布局
     */
    private String cleanArchitectureCode(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }
        
        logger.info("清理前的架构图代码: {}", code);
        
        // 确保使用 TD（从上到下），而不是 LR（左右）
        String cleaned = code;
        // 强制替换第一行的方向为 TD（这是最关键的）
        cleaned = cleaned.replaceFirst("(?i)^(flowchart|graph)\\s+(LR|TD|BT|RL)", "$1 TD");
        logger.info("已强制设置第一行为 flowchart TD");
        
        // 使用正则表达式匹配，确保替换所有情况（不区分大小写）
        java.util.regex.Pattern lrPattern = java.util.regex.Pattern.compile("(?i)(flowchart|graph)\\s+LR");
        java.util.regex.Matcher lrMatcher = lrPattern.matcher(cleaned);
        if (lrMatcher.find()) {
            cleaned = lrMatcher.replaceAll("$1 TD");
            logger.info("已将 flowchart/graph LR 改为 TD");
        }
        // 如果完全没有指定方向，添加 TD
        java.util.regex.Pattern dirPattern = java.util.regex.Pattern.compile("(?i)^(flowchart|graph)(\\s+(TD|LR|BT|RL))?");
        java.util.regex.Matcher dirMatcher = dirPattern.matcher(cleaned);
        if (!dirMatcher.find() || dirMatcher.group(2) == null) {
            cleaned = cleaned.replaceFirst("(?i)^(flowchart|graph)(\\s+(TD|LR|BT|RL))?", "$1 TD");
            logger.info("已添加 flowchart TD 方向");
        }
        
        // 再次强制确保第一行是 TD
        String firstLine = cleaned.split("\n")[0];
        if (!firstLine.matches("(?i)^(flowchart|graph)\\s+TD.*")) {
            cleaned = cleaned.replaceFirst("(?i)^(flowchart|graph)(\\s+(TD|LR|BT|RL))?", "$1 TD");
            logger.warn("最终强制修复：已设置第一行为 TD");
        }
        
        // 按行处理
        String[] lines = cleaned.split("\n");
        StringBuilder result = new StringBuilder();
        boolean inSubgraph = false;
        java.util.List<String> subgraphNodes = new java.util.ArrayList<>();
        boolean hasDirectionLR = false;
        String currentIndent = "";
        String previousSubgraphId = null; // 用于跟踪上一个 subgraph 的 ID
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            
            // 检测 subgraph 开始
            if (trimmed.startsWith("subgraph")) {
                // 提取 subgraph ID
                String subgraphId = trimmed.replaceFirst("subgraph\\s+", "").split("\\[")[0].trim();
                
                // 如果之前有 subgraph，添加一个不可见的连接来强制垂直布局
                if (previousSubgraphId != null && !previousSubgraphId.isEmpty()) {
                    // 使用不可见连接（使用 --- 但通过 linkStyle 隐藏）来强制垂直布局
                    // 先添加连接，后面会通过样式隐藏
                    result.append("    ").append(previousSubgraphId).append(" --- ").append(subgraphId).append("\n");
                }
                
                inSubgraph = true;
                subgraphNodes.clear();
                hasDirectionLR = false;
                currentIndent = line.substring(0, line.length() - trimmed.length());
                result.append(line).append("\n");
                previousSubgraphId = subgraphId;
                continue;
            }
            
            // 检测 subgraph 结束
            if (trimmed.equals("end") && inSubgraph) {
                // 如果没有 direction LR，添加它（用于水平布局）
                if (!hasDirectionLR && !subgraphNodes.isEmpty()) {
                    result.append(currentIndent).append("        direction LR\n");
                }
                
                // 添加所有节点，并在节点之间添加不可见的水平连接来强制水平布局
                java.util.List<String> nodeIds = new java.util.ArrayList<>();
                for (String node : subgraphNodes) {
                    result.append(node).append("\n");
                    // 提取节点ID（用于创建连接）
                    String nodeId = extractNodeIdFromLine(node);
                    if (nodeId != null && !nodeId.isEmpty()) {
                        nodeIds.add(nodeId);
                    }
                }
                
                // 在节点之间添加不可见的水平连接（用于强制水平布局）
                if (nodeIds.size() > 1) {
                    for (int j = 0; j < nodeIds.size() - 1; j++) {
                        result.append(currentIndent).append("        ").append(nodeIds.get(j))
                              .append(" --- ").append(nodeIds.get(j + 1)).append("\n");
                    }
                }
                
                inSubgraph = false;
                subgraphNodes.clear();
                result.append(line).append("\n");
                continue;
            }
            
            // 在 subgraph 内
            if (inSubgraph) {
                // 保留 direction LR（用于水平布局）
                if (trimmed.equals("direction LR") || trimmed.matches("^direction\\s+LR\\s*$")) {
                    hasDirectionLR = true;
                    result.append(line).append("\n");
                    continue;
                }
                
                // 移除 direction TD（应该使用 LR）
                if (trimmed.equals("direction TD") || trimmed.matches("^direction\\s+TD\\s*.*")) {
                    // 如果 direction TD 后面有其他内容，保留后面的内容
                    if (trimmed.matches("^direction\\s+TD\\s+.+")) {
                        java.util.regex.Pattern tdPattern = java.util.regex.Pattern.compile("^direction\\s+TD\\s+(.+)");
                        java.util.regex.Matcher tdMatcher = tdPattern.matcher(trimmed);
                        if (tdMatcher.find()) {
                            String afterDirection = tdMatcher.group(1).trim();
                            String indent = line.substring(0, line.length() - trimmed.length());
                            // 如果是连接语句，移除连接线，只收集节点
                            if (afterDirection.contains("-->") || afterDirection.contains("-.->")) {
                                // 移除连接线，只收集节点定义
                                String[] parts = afterDirection.split("-->|---|-\\.->");
                                for (String part : parts) {
                                    String nodeDef = part.trim();
                                    nodeDef = nodeDef.replaceAll("\\|.*?\\|", "").trim();
                                    if (!nodeDef.isEmpty() && (nodeDef.contains("[") || nodeDef.contains("(") || nodeDef.contains("{"))) {
                                        String nodeLine = indent + "        " + nodeDef;
                                        if (!subgraphNodes.contains(nodeLine)) {
                                            subgraphNodes.add(nodeLine);
                                        }
                                    }
                                }
                            } else {
                                // 如果是节点定义，添加到节点列表
                                String nodeLine = indent + "        " + afterDirection;
                                if (!subgraphNodes.contains(nodeLine)) {
                                    subgraphNodes.add(nodeLine);
                                }
                            }
                        }
                    }
                    // direction TD 本身被移除
                    continue;
                }
                
                // 移除所有连接线（架构图不需要连接线）
                // 匹配所有类型的连接线：-->, ---, <-->, <->, -.->, <==>, ==> 等
                if (trimmed.matches(".*(--|==|-\\.-|<-|->|<->|<-->|<-\\)|==>|<=>|<-\\|).*")) {
                    // 移除连接线，只收集节点定义
                    String[] parts = trimmed.split("-->|---|-\\.->|<-->|<->|==>|<=>|<-\\|");
                    String indent = line.substring(0, line.length() - trimmed.length());
                    for (String part : parts) {
                        String nodeDef = part.trim();
                        // 移除可能的标签（如 |标签|）
                        nodeDef = nodeDef.replaceAll("\\|.*?\\|", "").trim();
                        if (!nodeDef.isEmpty() && (nodeDef.contains("[") || nodeDef.contains("(") || nodeDef.contains("{"))) {
                            String nodeLine = indent + nodeDef;
                            if (!subgraphNodes.contains(nodeLine)) {
                                subgraphNodes.add(nodeLine);
                            }
                        }
                    }
                    // 不添加连接线，直接跳过
                    continue;
                }
                
                // 收集节点定义
                if (trimmed.contains("[") || trimmed.contains("(") || trimmed.contains("{")) {
                    if (!subgraphNodes.contains(line)) {
                        subgraphNodes.add(line);
                    }
                    continue;
                }
                
                // 其他行直接保留
                result.append(line).append("\n");
            } else {
                // 不在 subgraph 内，移除所有连接线但保留其他
                // 匹配所有类型的连接线
                if (trimmed.matches(".*(--|==|-\\.-|<-|->|<->|<-->|<-\\)|==>|<=>|<-\\|).*") && 
                    !trimmed.startsWith("subgraph") && !trimmed.equals("end")) {
                    // 移除连接线，只保留第一个节点定义
                    String[] parts = trimmed.split("-->|---|-\\.->|<-->|<->|==>|<=>|<-\\|");
                    if (parts.length > 0) {
                        String nodeDef = parts[0].trim();
                        nodeDef = nodeDef.replaceAll("\\|.*?\\|", "").trim();
                        if (!nodeDef.isEmpty() && (nodeDef.contains("[") || nodeDef.contains("(") || nodeDef.contains("{"))) {
                            String indent = line.substring(0, line.length() - trimmed.length());
                            result.append(indent).append(nodeDef).append("\n");
                        }
                    }
                } else {
                    result.append(line).append("\n");
                }
            }
        }
        
        cleaned = result.toString().trim();
        
        // 最后再次确保使用 TD（从上到下）
        if (cleaned.matches("(?s).*(flowchart|graph)\\s+LR.*")) {
            cleaned = cleaned.replaceAll("(?i)(flowchart|graph)\\s+LR", "$1 TD");
            logger.warn("最终修复：已将 flowchart/graph LR 改为 TD");
        }
        // 确保第一行是 flowchart TD 或 graph TD
        String finalFirstLine = cleaned.split("\n")[0];
        if (!finalFirstLine.matches("(?i)^(flowchart|graph)\\s+TD.*")) {
            cleaned = cleaned.replaceFirst("(?i)^(flowchart|graph)(\\s+(TD|LR|BT|RL))?", "$1 TD");
            logger.warn("最终修复：已强制设置第一行为 TD");
        }
        
        // 隐藏所有连接线（通过 linkStyle）
        // 计算连接线的索引（包括所有类型的连接：-->, ---, <-->, <->, -.->, <==>, ==> 等）
        int linkIndex = 0;
        // 按行处理，统计所有包含连接符的行
        String[] allLines = cleaned.split("\n");
        for (String line : allLines) {
            String trimmed = line.trim();
            // 匹配所有类型的连接线（排除注释、classDef、linkStyle 等）
            if (!trimmed.startsWith("%") && 
                !trimmed.startsWith("classDef") && 
                !trimmed.startsWith("linkStyle") &&
                !trimmed.startsWith("style") &&
                !trimmed.startsWith("subgraph") &&
                !trimmed.equals("end") &&
                trimmed.matches(".*\\w+\\s+([-<>=|.]+)\\s+\\w+.*")) {
                linkIndex++;
            }
        }
        // 在代码末尾添加隐藏连接的样式
        if (linkIndex > 0) {
            java.util.List<String> hiddenLinks = new java.util.ArrayList<>();
            for (int i = 0; i < linkIndex; i++) {
                hiddenLinks.add(String.format("    linkStyle %d stroke-width:0px,stroke:transparent", i));
            }
            cleaned = cleaned + "\n" + String.join("\n", hiddenLinks);
            logger.info("已添加 {} 个隐藏连接样式（包括所有类型的连接线）", hiddenLinks.size());
        }
        
        logger.info("清理后的架构图代码: {}", cleaned);
        
        return cleaned;
    }
    
    /**
     * 从节点定义行中提取节点ID
     * 例如：A[Web前端]:::presentation -> A
     *      Node1[文本] -> Node1
     */
    private String extractNodeIdFromLine(String nodeLine) {
        if (nodeLine == null || nodeLine.trim().isEmpty()) {
            return null;
        }
        String trimmed = nodeLine.trim();
        // 移除样式标记（:::style）
        trimmed = trimmed.replaceAll(":::[\\w]+", "").trim();
        // 提取节点ID（在 [、(、{ 之前的部分）
        int bracketIndex = trimmed.indexOf('[');
        int parenIndex = trimmed.indexOf('(');
        int braceIndex = trimmed.indexOf('{');
        
        int endIndex = trimmed.length();
        if (bracketIndex > 0 && bracketIndex < endIndex) endIndex = bracketIndex;
        if (parenIndex > 0 && parenIndex < endIndex) endIndex = parenIndex;
        if (braceIndex > 0 && braceIndex < endIndex) endIndex = braceIndex;
        
        if (endIndex > 0 && endIndex < trimmed.length()) {
            String nodeId = trimmed.substring(0, endIndex).trim();
            return nodeId.isEmpty() ? null : nodeId;
        }
        // 如果没有找到括号，尝试提取第一个单词
        String[] parts = trimmed.split("\\s+");
        return parts.length > 0 ? parts[0] : null;
    }
    
    
    @Override
    @Transactional
    public DrawIOHistoryResp saveHistory(DrawIOHistoryRequest request, Long userId) {
        try {
            logger.info("保存历史记录请求 - 用户ID: {}, 提示词: {}", userId, request.getPrompt());
            
            // 检查是否已存在相同的历史记录（避免重复）
            List<DrawIOHistory> existingHistories = drawIOHistoryRepository.findByUserIdAndNotDeleted(userId);
            String promptToSave = request.getPrompt();
            boolean exists = existingHistories.stream()
                    .anyMatch(h -> promptToSave.equals(h.getPrompt()));
            
            if (exists) {
                // 如果已存在，返回已存在的记录
                DrawIOHistory existing = existingHistories.stream()
                        .filter(h -> promptToSave.equals(h.getPrompt()))
                        .findFirst()
                        .orElse(null);
                if (existing != null) {
                    return convertHistoryToResp(existing);
                }
            }
            
            // 创建新历史记录
            DrawIOHistory history = new DrawIOHistory();
            history.setUserId(userId);
            history.setPrompt(request.getPrompt());
            history.setDiagramType(request.getDiagramType());
            SystemDateTimeUtil.setCreateTime(history);
            history.setDeleted(0);
            
            // 保存前检查，如果历史记录超过10条，删除最旧的
            List<DrawIOHistory> allHistories = drawIOHistoryRepository.findByUserIdAndNotDeleted(userId);
            if (allHistories.size() >= 10) {
                // 按创建时间排序，删除最旧的
                allHistories.sort((a, b) -> a.getCreateTime().compareTo(b.getCreateTime()));
                for (int i = 0; i < allHistories.size() - 9; i++) {
                    DrawIOHistory oldHistory = allHistories.get(i);
                    oldHistory.setDeleted(1);
                    drawIOHistoryRepository.save(oldHistory);
                }
            }
            
            history = drawIOHistoryRepository.save(history);
            
            logger.info("历史记录保存成功 - ID: {}, 用户ID: {}", history.getId(), userId);
            
            return convertHistoryToResp(history);
            
        } catch (Exception e) {
            logger.error("保存历史记录失败 - 用户ID: {}, 提示词: {}", userId, request.getPrompt(), e);
            throw new RuntimeException("保存历史记录失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<DrawIOHistoryResp> getHistoryList(Long userId) {
        try {
            // 获取最多10条历史记录
            List<DrawIOHistory> histories = drawIOHistoryRepository.findByUserIdAndNotDeletedLimit(userId);
            return histories.stream()
                    .map(this::convertHistoryToResp)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取历史记录列表失败 - 用户ID: {}", userId, e);
            throw new RuntimeException("获取历史记录列表失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    @Transactional
    public void deleteHistory(Long id, Long userId) {
        try {
            DrawIOHistory history = drawIOHistoryRepository.findByIdAndUserId(id, userId)
                    .orElseThrow(() -> new RuntimeException("历史记录不存在或无权限访问"));
            
            history.setDeleted(1);
            drawIOHistoryRepository.save(history);
            
            logger.info("历史记录删除成功 - ID: {}, 用户ID: {}", id, userId);
        } catch (Exception e) {
            logger.error("删除历史记录失败 - ID: {}, 用户ID: {}", id, userId, e);
            throw new RuntimeException("删除历史记录失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 转换为历史记录响应对象
     */
    private DrawIOHistoryResp convertHistoryToResp(DrawIOHistory history) {
        DrawIOHistoryResp resp = new DrawIOHistoryResp();
        resp.setId(history.getId());
        resp.setUserId(history.getUserId());
        resp.setPrompt(history.getPrompt());
        resp.setDiagramType(history.getDiagramType());
        resp.setCreateTime(history.getCreateTime());
        return resp;
    }
}


