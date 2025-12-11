package com.github.app.dify.appsystemdata.service.impl;

import com.github.app.dify.appsystemdata.domain.SystemConfig;
import com.github.app.dify.appsystemdata.repository.SystemConfigRepository;
import com.github.app.dify.appsystemdata.req.UpdateSystemConfigReq;
import com.github.app.dify.appsystemdata.resp.SystemConfigResp;
import com.github.app.dify.appsystemdata.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 系统配置服务实现
 */
@Service
public class SystemConfigServiceImpl implements SystemConfigService {
    
    private static final Logger logger = LoggerFactory.getLogger(SystemConfigServiceImpl.class);
    
    @Autowired
    private SystemConfigRepository systemConfigRepository;
    
    @Override
    public String getConfigValue(String configKey) {
        Optional<SystemConfig> optional = systemConfigRepository.findByConfigKeyAndNotDeleted(configKey);
        return optional.map(SystemConfig::getConfigValue).orElse(null);
    }
    
    @Override
    public SystemConfigResp getConfigByKey(String configKey) {
        Optional<SystemConfig> optional = systemConfigRepository.findByConfigKeyAndNotDeleted(configKey);
        return optional.map(this::convertToResp).orElse(null);
    }
    
    @Override
    public List<SystemConfigResp> getConfigsByGroup(String configGroup) {
        List<SystemConfig> configs = systemConfigRepository.findByConfigGroupAndNotDeleted(configGroup);
        return configs.stream().map(this::convertToResp).collect(Collectors.toList());
    }
    
    @Override
    public List<SystemConfigResp> getAllConfigs() {
        List<SystemConfig> configs = systemConfigRepository.findAllNotDeleted();
        return configs.stream().map(this::convertToResp).collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public SystemConfigResp setOrUpdateConfig(UpdateSystemConfigReq req, Long userId, String username) {
        Optional<SystemConfig> optional = systemConfigRepository.findByConfigKeyAndNotDeleted(req.getConfigKey());
        
        SystemConfig config;
        if (optional.isPresent()) {
            // 更新现有配置
            config = optional.get();
            logger.info("更新系统配置 - 键: {}, 用户: {}", req.getConfigKey(), username);
        } else {
            // 创建新配置
            config = new SystemConfig();
            config.setConfigKey(req.getConfigKey());
            config.setCreateTime(new Date());
            config.setCreator(username);
            config.setCreatorId(userId);
            config.setDeleted(0);
            logger.info("创建系统配置 - 键: {}, 用户: {}", req.getConfigKey(), username);
        }
        
        // 更新字段
        if (req.getConfigValue() != null) {
            config.setConfigValue(req.getConfigValue());
        }
        if (req.getDescription() != null) {
            config.setDescription(req.getDescription());
        }
        if (req.getConfigGroup() != null) {
            config.setConfigGroup(req.getConfigGroup());
        }
        if (req.getConfigType() != null) {
            config.setConfigType(req.getConfigType());
        }
        
        config.setUpdateTime(new Date());
        
        config = systemConfigRepository.save(config);
        
        logger.info("系统配置保存成功 - 键: {}, ID: {}", config.getConfigKey(), config.getId());
        
        return convertToResp(config);
    }
    
    @Override
    @Transactional
    public void deleteConfig(String configKey) {
        Optional<SystemConfig> optional = systemConfigRepository.findByConfigKeyAndNotDeleted(configKey);
        if (optional.isPresent()) {
            SystemConfig config = optional.get();
            config.setDeleted(1);
            config.setUpdateTime(new Date());
            systemConfigRepository.save(config);
            logger.info("删除系统配置 - 键: {}", configKey);
        } else {
            logger.warn("要删除的配置不存在 - 键: {}", configKey);
        }
    }
    
    /**
     * 转换为响应对象
     */
    private SystemConfigResp convertToResp(SystemConfig config) {
        SystemConfigResp resp = new SystemConfigResp();
        BeanUtils.copyProperties(config, resp);
        return resp;
    }
}

