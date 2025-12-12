package com.github.app.dify.system.service.impl;

import com.github.app.dify.system.config.DifyConfig;
import com.github.app.dify.system.domain.SystemConfig;
import com.github.app.dify.system.repository.SystemConfigRepository;
import com.github.app.dify.system.req.UpdateSystemConfigReq;
import com.github.app.dify.system.resp.SystemConfigResp;
import com.github.app.dify.system.service.SystemConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
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
    
    // Dify 配置键前缀
    private static final String DIFY_CONFIG_PREFIX = "dify.api.";
    
    @Autowired
    private SystemConfigRepository systemConfigRepository;
    
    @Autowired
    private ApplicationContext applicationContext;
    
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
        
        // 如果更新的是 Dify 相关配置，重新加载 DifyConfig
        if (config.getConfigKey() != null && config.getConfigKey().startsWith(DIFY_CONFIG_PREFIX)) {
            reloadDifyConfig();
        }
        
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
     * 重新加载 Dify 配置
     */
    private void reloadDifyConfig() {
        try {
            DifyConfig difyConfig = applicationContext.getBean(DifyConfig.class);
            if (difyConfig != null) {
                difyConfig.reload();
                logger.info("Dify 配置已重新加载");
            }
        } catch (Exception e) {
            // DifyConfig 可能不存在，忽略错误
            logger.debug("重新加载 Dify 配置失败（可能 DifyConfig 未初始化）: {}", e.getMessage());
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

