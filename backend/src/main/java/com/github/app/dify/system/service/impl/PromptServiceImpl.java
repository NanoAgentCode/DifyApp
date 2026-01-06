package com.github.app.dify.system.service.impl;

import com.github.app.dify.system.domain.Prompt;
import com.github.app.dify.system.repository.PromptRepository;
import com.github.app.dify.system.req.CreatePromptReq;
import com.github.app.dify.system.req.UpdatePromptReq;
import com.github.app.dify.system.resp.PromptResp;
import com.github.app.dify.system.service.PromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.github.app.dify.system.util.SystemConverterUtil;
import com.github.app.dify.system.util.SystemDateTimeUtil;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 提示词服务实现
 */
@Service
public class PromptServiceImpl implements PromptService {
    
    private static final Logger logger = LoggerFactory.getLogger(PromptServiceImpl.class);
    
    @Autowired
    private PromptRepository promptRepository;
    
    @Override
    @Transactional
    public PromptResp createPrompt(CreatePromptReq req) {
        // 检查是否存在相同标题的提示词
        List<Prompt> existing = promptRepository.findByTitleAndNotDeleted(req.getTitle());
        if (!existing.isEmpty()) {
            throw new RuntimeException("已存在标题为 \"" + req.getTitle() + "\" 的提示词");
        }
        
        Prompt prompt = new Prompt();
        prompt.setTitle(req.getTitle());
        prompt.setContent(req.getContent());
        prompt.setDeleted(0); // 默认未删除
        SystemDateTimeUtil.setCreateAndUpdateTime(prompt);
        
        prompt = promptRepository.save(prompt);
        
        logger.info("创建提示词成功 - ID: {}, 标题: {}", prompt.getId(), prompt.getTitle());
        
        return SystemConverterUtil.convertToResp(prompt);
    }
    
    @Override
    @Transactional
    public PromptResp updatePrompt(Long id, UpdatePromptReq req) {
        Optional<Prompt> optional = promptRepository.findById(id);
        if (!optional.isPresent()) {
            throw new RuntimeException("提示词不存在: " + id);
        }
        
        Prompt prompt = optional.get();
        
        // 检查是否已删除
        if (prompt.getDeleted() != null && prompt.getDeleted() == 1) {
            throw new RuntimeException("提示词已被删除");
        }
        
        // 如果更新标题，检查是否与其他提示词重复
        if (req.getTitle() != null && !req.getTitle().equals(prompt.getTitle())) {
            List<Prompt> existing = promptRepository.findByTitleAndNotDeleted(req.getTitle());
            if (!existing.isEmpty() && !existing.get(0).getId().equals(id)) {
                throw new RuntimeException("已存在标题为 \"" + req.getTitle() + "\" 的提示词");
            }
        }
        
        // 更新字段
        if (req.getTitle() != null) {
            prompt.setTitle(req.getTitle());
        }
        if (req.getContent() != null) {
            prompt.setContent(req.getContent());
        }
        SystemDateTimeUtil.setUpdateTime(prompt);
        
        prompt = promptRepository.save(prompt);
        
        logger.info("更新提示词成功 - ID: {}, 标题: {}", prompt.getId(), prompt.getTitle());
        
        return SystemConverterUtil.convertToResp(prompt);
    }
    
    @Override
    @Transactional
    public void deletePrompt(Long id) {
        Optional<Prompt> optional = promptRepository.findById(id);
        if (!optional.isPresent()) {
            throw new RuntimeException("提示词不存在: " + id);
        }
        
        Prompt prompt = optional.get();
        prompt.setDeleted(1);
        SystemDateTimeUtil.setUpdateTime(prompt);
        
        promptRepository.save(prompt);
        
        logger.info("删除提示词成功 - ID: {}, 标题: {}", prompt.getId(), prompt.getTitle());
    }
    
    @Override
    public Prompt getPromptEntityById(Long id) {
        Optional<Prompt> optional = promptRepository.findById(id);
        if (!optional.isPresent()) {
            throw new RuntimeException("提示词不存在: " + id);
        }
        
        Prompt prompt = optional.get();
        
        // 检查是否已删除
        if (prompt.getDeleted() != null && prompt.getDeleted() == 1) {
            throw new RuntimeException("提示词已被删除");
        }
        
        return prompt;
    }
    
    @Override
    public PromptResp getPromptById(Long id) {
        return convertToResp(getPromptEntityById(id));
    }
    
    @Override
    public List<PromptResp> listPrompts(String keyword) {
        List<Prompt> prompts;
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            prompts = promptRepository.findByTitleOrContentContaining(keyword.trim());
        } else {
            prompts = promptRepository.findAllNotDeleted();
        }
        
        return prompts.stream()
                .map(this::convertToResp)
                .collect(Collectors.toList());
    }
    
}
