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
import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.github.app.dify.system.util.SkillLoader;
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
            logger.info("生成图表请求 - 用户ID: {}, 提示: {}, 类型: {}",
                    userId,
                    request.getPrompt(),
                    request.getDiagramType());

            // 获取模型
            QAModel qaModel = getQAModel(request.getModelId());

            // 构建系统提示词
            String systemPrompt = buildSystemPrompt(request.getDiagramType());

            // 构建用户提示词
            String userPrompt = buildUserPrompt(request.getPrompt(), request.getDiagramType());

            // 创建模型实例
            ChatLanguageModel chatLanguageModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);

            // 构建消息
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt));

            // 调用LLM生成图表JSON
            Response<AiMessage> response = chatLanguageModel.generate(messages);
            String diagramJson = extractDiagramJson(response.content().text(), request.getDiagramType());

            logger.info("图表生成成功 - 用户ID: {}, JSON长度: {}",
                    userId,
                    diagramJson.length());

            // 构建响应
            DrawIOGenerateResponse generateResponse = new DrawIOGenerateResponse();
            generateResponse.setDiagramJson(diagramJson);
            generateResponse.setDiagramType(request.getDiagramType());

            return generateResponse;

        } catch (Exception e) {
            logger.error("生成图表失败 - 用户ID: {}, 提示: {}",
                    userId,
                    request.getPrompt(), e);
            throw new BusinessException("生成图表失败，请稍后重试", ErrorCode.API_CALL_FAILED, e);
        }
    }

    @Override
    public DrawIOGenerateResponse modifyDiagram(DrawIOModifyRequest request, Long userId) {
        try {
            logger.info("修改图表请求 - 用户ID: {}, 修改指令: {}",
                    userId,
                    request.getPrompt());

            // 获取模型
            QAModel qaModel = getQAModel(request.getModelId());

            // 构建系统提示词
            String systemPrompt = buildModifySystemPrompt();

            // 构建用户提示词
            String userPrompt = buildModifyUserPrompt(request.getDiagramJson(), request.getPrompt());

            // 创建模型实例
            ChatLanguageModel chatLanguageModel = modelLanguageModelFactory.createChatLanguageModel(qaModel);

            // 构建消息
            List<ChatMessage> messages = List.of(
                    SystemMessage.from(systemPrompt),
                    UserMessage.from(userPrompt));

            // 调用LLM修改图表JSON
            Response<AiMessage> response = chatLanguageModel.generate(messages);
            // 从现有图表代码中推断类型（通过检查代码开头）
            String diagramType = inferDiagramType(request.getDiagramJson());
            String diagramJson = extractDiagramJson(response.content().text(), diagramType);

            logger.info("图表修改成功 - 用户ID: {}, JSON长度: {}",
                    userId,
                    diagramJson.length());

            // 构建响应
            DrawIOGenerateResponse generateResponse = new DrawIOGenerateResponse();
            generateResponse.setDiagramJson(diagramJson);

            return generateResponse;

        } catch (Exception e) {
            logger.error("修改图表失败 - 用户ID: {}, 修改指令: {}",
                    userId,
                    request.getPrompt(), e);
            throw new BusinessException("修改图表失败，请稍后重试", ErrorCode.API_CALL_FAILED, e);
        }
    }

    @Override
    @Transactional
    public DrawIODiagramResp saveDiagram(DrawIOSaveRequest request, Long userId) {
        try {
            logger.info("保存图表请求 - 用户ID: {}, 名称: {}",
                    userId,
                    request.getName());

            DrawIODiagram diagram = new DrawIODiagram();
            diagram.setName(request.getName());
            diagram.setDiagramType(request.getDiagramType());
            diagram.setDiagramJson(request.getDiagramJson());
            diagram.setUserId(userId);
            SystemDateTimeUtil.setCreateAndUpdateTime(diagram);
            diagram.setDeleted(0);

            diagram = drawIODiagramRepository.save(diagram);

            logger.info("图表保存成功 - ID: {}, 用户ID: {}",
                    diagram.getId(),
                    userId);

            return SystemConverterUtil.convertToResp(diagram);

        } catch (Exception e) {
            logger.error("保存图表失败 - 用户ID: {}, 名称: {}",
                    userId,
                    request.getName(), e);
            throw new BusinessException("保存图表失败", ErrorCode.DATABASE_ERROR, e);
        }
    }

    @Override
    public List<DrawIODiagramResp> getDiagramList(Long userId) {
        try {
            List<DrawIODiagram> diagrams = drawIODiagramRepository.findByUserIdAndNotDeleted(userId);
            return diagrams.stream()
                    .map(SystemConverterUtil::convertToResp)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("获取图表列表失败 - 用户ID: {}",
                    userId, e);
            throw new BusinessException("获取图表列表失败", ErrorCode.DATABASE_ERROR, e);
        }
    }

    @Override
    public DrawIODiagramResp getDiagramDetail(Long id, Long userId) {
        try {
            DrawIODiagram diagram = drawIODiagramRepository.findByIdAndUserId(id, userId)
                    .orElseThrow(() -> new BusinessException("图表不存在或无权限访问", ErrorCode.FORBIDDEN));

            return SystemConverterUtil.convertToResp(diagram);
        } catch (Exception e) {
            logger.error("获取图表详情失败 - ID: {}, 用户ID: {}",
                    id,
                    userId, e);
            throw new BusinessException("获取图表详情失败", ErrorCode.DATABASE_ERROR, e);
        }
    }

    @Override
    @Transactional
    public void deleteDiagram(Long id, Long userId) {
        try {
            DrawIODiagram diagram = drawIODiagramRepository.findByIdAndUserId(id, userId)
                    .orElseThrow(() -> new BusinessException("图表不存在或无权限访问", ErrorCode.FORBIDDEN));

            diagram.setDeleted(1);
            SystemDateTimeUtil.setUpdateTime(diagram);
            drawIODiagramRepository.save(diagram);

            logger.info("图表删除成功 - ID: {}, 用户ID: {}",
                    id,
                    userId);
        } catch (Exception e) {
            logger.error("删除图表失败 - ID: {}, 用户ID: {}",
                    id,
                    userId, e);
            throw new BusinessException("删除图表失败", ErrorCode.DATABASE_ERROR, e);
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
        String base = SkillLoader.loadSkill("drawio/base");
        StringBuilder prompt = new StringBuilder();
        if (base != null && !base.trim().isEmpty()) {
            prompt.append(base.trim());
        } else {
            // 如果系统提示词文件不存在，使用 fallback
            String fallback = SkillLoader.loadSkill("drawio/base_fallback");
            if (fallback != null && !fallback.trim().isEmpty()) {
                prompt.append(fallback.trim());
            } else {
                // 最后的默认提示词
                prompt.append("你是一个专业的图表生成助手，专门生成 AntV Infographic 格式的图表代码。\n\n");
                prompt.append("重要要求：\n");
                prompt.append("1. 你必须只返回有效的 AntV Infographic DSL 代码，不要包含任何解释文字\n");
                prompt.append("2. 代码必须符合 AntV Infographic 语法规范\n");
                prompt.append("3. 使用中文标签和文本\n");
                prompt.append("4. 代码块不要使用 ``` 包裹，直接返回代码\n");
            }
        }

        if (diagramType != null) {
            String typeSkill = SkillLoader.loadSkill("drawio/" + diagramType);
            if (typeSkill != null && !typeSkill.trim().isEmpty()) {
                prompt.append("\n\n").append(typeSkill.trim());
            } else {
                logger.warn("未找到图表类型提示词: drawio/{}", diagramType);
            }
        }

        prompt.append("\n请直接返回完整的 AntV Infographic DSL 代码，不要包含任何其他文字说明，不要使用代码块包裹。");

        return prompt.toString();
    }

    /**
     * 构建用户提示词（生成）
     */
    private String buildUserPrompt(String userPrompt, String diagramType) {
        // 根据图表类型获取特定要求
        String typeSpecificRequirement = "";
        if (diagramType != null) {
            switch (diagramType) {
                case "flowchart":
                    typeSpecificRequirement = "流程图必须包含所有步骤、判断分支、循环等完整流程，至少 8-15 个步骤";
                    break;
                case "architecture":
                    typeSpecificRequirement = "架构图必须包含所有层级和组件，每层至少 3-5 个组件，至少 3-5 层";
                    break;
                case "mindmap":
                    typeSpecificRequirement = "思维导图必须包含所有主要分支和子分支，至少 3-4 个主要分支，每个分支至少 2-3 个子分支";
                    break;
                case "sequence":
                    typeSpecificRequirement = "时序图必须包含所有参与者和完整的交互序列，至少 4-6 个参与者，10-15 条消息";
                    break;
                case "uml":
                    typeSpecificRequirement = "UML图必须包含所有相关的类和关系，至少 5-8 个类";
                    break;
                case "org":
                    typeSpecificRequirement = "组织架构图必须包含所有层级和人员，至少 3-4 层";
                    break;
                case "network":
                    typeSpecificRequirement = "网络图必须包含所有网络设备和连接，至少 8-12 个设备节点";
                    break;
            }
        }

        // 使用模板加载用户提示词
        Map<String, String> variables = new HashMap<>();
        variables.put("userPrompt", userPrompt);
        variables.put("typeSpecificRequirement", typeSpecificRequirement);

        String template = SkillLoader.loadSkillWithTemplate("drawio/user_prompt_template", variables);
        if (template != null && !template.trim().isEmpty()) {
            return template;
        }

        // Fallback：如果模板不存在，使用硬编码方式
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下描述生成详细、完整的 AntV Infographic 格式图表代码：\n\n");
        prompt.append(userPrompt);
        prompt.append("\n\n");
        prompt.append("重要要求：\n");
        prompt.append("1. 必须生成详细、完整的图表，包含所有相关节点和细节\n");
        prompt.append("2. 不要简化或省略任何重要部分，要充分展开用户描述的所有内容\n");
        if (!typeSpecificRequirement.isEmpty()) {
            prompt.append("3. ").append(typeSpecificRequirement).append("\n");
        }
        prompt.append("4. 请生成完整的 AntV Infographic DSL 代码，不要使用代码块包裹。");
        return prompt.toString();
    }

    /**
     * 构建系统提示词（修改）
     */
    private String buildModifySystemPrompt() {
        String base = SkillLoader.loadSkill("drawio/modify_system_prompt");
        if (base != null && !base.trim().isEmpty()) {
            return base;
        }
        // 使用 fallback
        String fallback = SkillLoader.loadSkill("drawio/modify_system_prompt_fallback");
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback;
        }
        // 最后的默认提示词
        return "你是一个专业的图表修改助手，专门修改 AntV Infographic 格式的图表代码。\n\n" +
                "重要要求：\n" +
                "1. 你必须只返回修改后的完整 AntV Infographic DSL 代码，不要包含任何解释文字\n" +
                "2. 保持原有图表的整体结构和风格（包括模板名称、主题等）\n" +
                "3. 根据修改指令进行精确修改\n" +
                "4. 确保修改后的代码仍然有效且符合 AntV Infographic 语法规范\n" +
                "5. 使用中文标签和文本\n" +
                "6. 不要使用代码块包裹，直接返回代码\n\n" +
                "请直接返回完整的修改后的 AntV Infographic DSL 代码，不要包含任何其他文字说明。";
    }

    /**
     * 构建用户提示词（修改）
     */
    private String buildModifyUserPrompt(String diagramJson, String modifyInstruction) {
        // 只传递代码的关键部分，避免过长
        String diagramCode = diagramJson.length() > 5000
                ? diagramJson.substring(0, 5000) + "..."
                : diagramJson;

        // 使用模板加载用户提示词
        Map<String, String> variables = new HashMap<>();
        variables.put("diagramJson", diagramCode);
        variables.put("modifyInstruction", modifyInstruction);

        String template = SkillLoader.loadSkillWithTemplate("drawio/modify_user_prompt_template", variables);
        if (template != null && !template.trim().isEmpty()) {
            return template;
        }

        // Fallback：如果模板不存在，使用硬编码方式
        StringBuilder prompt = new StringBuilder();
        prompt.append("现有图表代码：\n");
        prompt.append("```\n");
        prompt.append(diagramCode).append("\n");
        prompt.append("```\n\n");
        prompt.append("修改指令：\n");
        prompt.append(modifyInstruction);
        prompt.append("\n\n请根据修改指令生成修改后的完整 AntV Infographic DSL 代码，不要使用代码块包裹。");
        return prompt.toString();
    }

    /**
     * 推断图表类型（从 AntV Infographic 代码中提取模板名称）
     */
    private String inferDiagramType(String diagramJson) {
        if (diagramJson == null || diagramJson.isEmpty()) {
            return null;
        }
        String trimmed = diagramJson.trim();

        // 检查是否是 AntV Infographic 格式（以 infographic 开头）
        if (trimmed.startsWith("infographic")) {
            // 从模板名称推断图表类型
            String lowerCase = trimmed.toLowerCase();
            if (lowerCase.contains("sequence") || lowerCase.contains("horizontal-arrow") ||
                    lowerCase.contains("steps") || lowerCase.contains("stairs")) {
                return "flowchart";
            } else if (lowerCase.contains("hierarchy") || lowerCase.contains("structure") ||
                    lowerCase.contains("tree")) {
                // 需要进一步判断是架构图还是思维导图
                if (lowerCase.contains("mindmap")) {
                    return "mindmap";
                } else {
                    return "architecture";
                }
            } else if (lowerCase.contains("timeline") || lowerCase.contains("ascending")) {
                return "sequence";
            } else if (lowerCase.contains("relation") || lowerCase.contains("dagre") ||
                    lowerCase.contains("circle")) {
                // 需要进一步判断是 UML 还是网络图
                // 这里可以根据上下文或默认返回 uml
                return "uml";
            }
        }

        // 如果没有匹配到，尝试从代码内容推断
        // 检查是否包含层级结构（children）
        if (trimmed.contains("children")) {
            if (trimmed.contains("mindmap")) {
                return "mindmap";
            } else {
                return "architecture";
            }
        }

        return null;
    }

    /**
     * 从LLM响应中提取 AntV Infographic 代码
     */
    private String extractDiagramJson(String response, String diagramType) {
        String extracted = null;

        // 尝试提取代码块
        if (response.contains("```")) {
            int start = response.indexOf("```");
            // 跳过开头的 ```
            String afterStart = response.substring(start + 3).trim();
            // 查找下一个 ```
            int end = afterStart.indexOf("```");
            if (end > 0) {
                extracted = afterStart.substring(0, end).trim();
            } else {
                // 如果没有结束标记，使用剩余内容
                extracted = afterStart.trim();
            }
        }

        // 如果都没有，检查原始响应是否已经是 AntV Infographic 代码
        if (extracted == null) {
            String trimmed = response.trim();
            // 检查是否以 infographic 开头
            if (trimmed.startsWith("infographic")) {
                extracted = trimmed;
            } else {
                // 即使不是标准格式，也尝试提取
                extracted = trimmed;
            }
        }

        // 清理和验证 AntV Infographic 代码
        if (extracted != null) {
            extracted = cleanInfographicCode(extracted);
        }

        return extracted;
    }

    /**
     * 清理 AntV Infographic 代码
     */
    private String cleanInfographicCode(String code) {
        if (code == null || code.isEmpty()) {
            return code;
        }

        String cleaned = code.trim();

        // 移除可能的代码块标记
        cleaned = cleaned.replaceAll("^```[a-z]*\\s*", "");
        cleaned = cleaned.replaceAll("```\\s*$", "");

        // 确保以 infographic 开头
        if (!cleaned.startsWith("infographic")) {
            // 尝试查找 infographic 关键字
            int infographicIndex = cleaned.indexOf("infographic");
            if (infographicIndex >= 0) {
                cleaned = cleaned.substring(infographicIndex).trim();
            }
        }

        // 移除多余的空行（保留必要的空行用于格式化）
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");

        return cleaned.trim();
    }

    @Override
    @Transactional
    public DrawIOHistoryResp saveHistory(DrawIOHistoryRequest request, Long userId) {
        try {
            logger.info("保存历史记录请求 - 用户ID: {}, 提示词: {}",
                    userId,
                    request.getPrompt());

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
                    boolean shouldUpdate = false;
                    if (request.getDiagramJson() != null && !request.getDiagramJson().trim().isEmpty()) {
                        if (existing.getDiagramJson() == null
                                || !request.getDiagramJson().equals(existing.getDiagramJson())) {
                            existing.setDiagramJson(request.getDiagramJson());
                            shouldUpdate = true;
                        }
                    }
                    if (request.getDiagramType() != null && !request.getDiagramType().trim().isEmpty()) {
                        if (existing.getDiagramType() == null
                                || !request.getDiagramType().equals(existing.getDiagramType())) {
                            existing.setDiagramType(request.getDiagramType());
                            shouldUpdate = true;
                        }
                    }
                    if (shouldUpdate) {
                        drawIOHistoryRepository.save(existing);
                    }
                    return convertHistoryToResp(existing);
                }
            }

            // 创建新历史记录
            DrawIOHistory history = new DrawIOHistory();
            history.setUserId(userId);
            history.setPrompt(request.getPrompt());
            history.setDiagramType(request.getDiagramType());
            history.setDiagramJson(request.getDiagramJson());
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

            logger.info("历史记录保存成功 - ID: {}, 用户ID: {}",
                    history.getId(),
                    userId);

            return convertHistoryToResp(history);

        } catch (Exception e) {
            logger.error("保存历史记录失败 - 用户ID: {}, 提示词: {}",
                    userId,
                    request.getPrompt(), e);
            throw new BusinessException("保存历史记录失败", ErrorCode.DATABASE_ERROR, e);
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
            logger.error("获取历史记录列表失败 - 用户ID: {}",
                    userId, e);
            throw new BusinessException("获取历史记录列表失败", ErrorCode.DATABASE_ERROR, e);
        }
    }

    @Override
    @Transactional
    public void deleteHistory(Long id, Long userId) {
        try {
            DrawIOHistory history = drawIOHistoryRepository.findByIdAndUserId(id, userId)
                    .orElseThrow(() -> new BusinessException("历史记录不存在或无权限访问", ErrorCode.FORBIDDEN));

            history.setDeleted(1);
            drawIOHistoryRepository.save(history);

            logger.info("历史记录删除成功 - ID: {}, 用户ID: {}",
                    id,
                    userId);
        } catch (Exception e) {
            logger.error("删除历史记录失败 - ID: {}, 用户ID: {}",
                    id,
                    userId, e);
            throw new BusinessException("删除历史记录失败", ErrorCode.DATABASE_ERROR, e);
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
        resp.setDiagramJson(history.getDiagramJson());
        resp.setCreateTime(history.getCreateTime());
        return resp;
    }
}
