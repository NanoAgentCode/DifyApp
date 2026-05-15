package com.github.app.dify.system.service.impl;

import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.system.domain.SystemConfig;
import com.github.app.dify.system.repository.SystemConfigRepository;
import com.github.app.dify.system.service.SystemConfigService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 系统配置服务实现（从 SYSTEM_CONFIG 表按 key 读取）
 */
@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;

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

    @Override
    public List<SystemConfig> getConfigsByGroup(String configGroup) {
        if (configGroup == null || configGroup.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return systemConfigRepository.findByConfigGroupAndNotDeleted(configGroup.trim());
    }

    @Override
    public List<SystemConfig> getAllConfigs() {
        return systemConfigRepository.findByDeletedOrderByConfigGroupAscConfigKeyAsc(NOT_DELETED);
    }

    @Override
    public PageResponse<SystemConfig> getConfigsWithPagination(String keyword, String configGroup, int page, int pageSize) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        String normalizedGroup = configGroup == null ? null : configGroup.trim();
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        Pageable pageable = PageRequest.of(
                safePage - 1,
                safePageSize,
                Sort.by(Sort.Order.asc("configGroup"), Sort.Order.asc("configKey"))
        );
        org.springframework.data.domain.Page<SystemConfig> result =
                systemConfigRepository.searchConfigs(NOT_DELETED, normalizedGroup, normalizedKeyword, pageable);
        return new PageResponse<>(result.getContent(), result.getTotalElements(), safePage, safePageSize);
    }

    @Override
    public Optional<SystemConfig> getConfigByKey(String configKey) {
        if (configKey == null || configKey.trim().isEmpty()) {
            return Optional.empty();
        }
        return systemConfigRepository.findByConfigKeyAndNotDeleted(configKey.trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemConfig saveOrUpdate(SystemConfig config, Long creatorId, String creator) {
        if (config == null || config.getConfigKey() == null || config.getConfigKey().trim().isEmpty()) {
            throw new IllegalArgumentException("配置键不能为空");
        }
        String key = config.getConfigKey().trim();
        Date now = new Date();
        Optional<SystemConfig> existing = systemConfigRepository.findByConfigKeyAndNotDeleted(key);
        if (existing.isPresent()) {
            SystemConfig entity = existing.get();
            entity.setConfigValue(config.getConfigValue());
            entity.setConfigGroup(config.getConfigGroup());
            entity.setConfigType(config.getConfigType());
            entity.setDescription(config.getDescription());
            entity.setUpdateTime(now);
            return systemConfigRepository.save(entity);
        }
        SystemConfig entity = new SystemConfig();
        entity.setConfigKey(key);
        entity.setConfigValue(config.getConfigValue());
        entity.setConfigGroup(config.getConfigGroup());
        entity.setConfigType(config.getConfigType());
        entity.setDescription(config.getDescription());
        entity.setCreator(creator);
        entity.setCreatorId(creatorId);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(NOT_DELETED);
        return systemConfigRepository.save(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByKey(String configKey) {
        if (configKey == null || configKey.trim().isEmpty()) {
            return;
        }
        systemConfigRepository.findByConfigKeyAndNotDeleted(configKey.trim()).ifPresent(entity -> {
            entity.setDeleted(DELETED);
            entity.setUpdateTime(new Date());
            systemConfigRepository.save(entity);
        });
    }
}
