package com.github.app.dify.system.service.impl;

import com.github.app.dify.system.domain.SystemConfig;
import com.github.app.dify.system.repository.SystemConfigRepository;
import com.github.app.dify.system.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 系统配置服务实现（从 SYSTEM_CONFIG 表按 key 读取）
 */
@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Override
    public String getConfigValue(String configKey) {
        if (configKey == null || configKey.trim().isEmpty()) {
            return null;
        }
        Optional<SystemConfig> optional = systemConfigRepository.findByConfigKeyAndNotDeleted(configKey.trim());
        return optional.map(SystemConfig::getConfigValue).orElse(null);
    }
}
