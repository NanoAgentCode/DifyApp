package com.github.app.dify.userlog.service.impl;
import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.userlog.document.UserActionLogDocument;
import com.github.app.dify.userlog.domain.UserActionLog;
import com.github.app.dify.userlog.req.UserActionLogQueryReq;
import com.github.app.dify.userlog.resp.UserActionLogResp;
import com.github.app.dify.userlog.service.ElasticsearchLogService;
import com.github.app.dify.userlog.service.UserActionLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户行为日志Service实现（仅Elasticsearch存储）
 */
@Service
public class UserActionLogServiceImpl implements UserActionLogService {

    private static final Logger logger = LoggerFactory.getLogger(UserActionLogServiceImpl.class);

    @Autowired
    private ElasticsearchLogService elasticsearchLogService;

    /**
     * 异步保存日志（仅保存到Elasticsearch）
     */
    @Async
    @Override
    public void saveLog(UserActionLog log) {
        try {
            // 仅保存到Elasticsearch
            if (elasticsearchLogService.isEnabled()) {
                UserActionLogDocument document = convertToDocument(log);
                elasticsearchLogService.saveLog(document);
                logger.debug("用户行为日志已保存到Elasticsearch: userId={}, module={}, actionType={}", 
                        log.getUserId(), log.getModule(), log.getActionType());
            } else {
                logger.warn("Elasticsearch未启用，无法保存日志");
            }
        } catch (Exception e) {
            logger.error("保存用户行为日志到Elasticsearch失败", e);
        }
    }

    /**
     * 分页查询日志（仅从Elasticsearch查询）
     */
    @Override
    public PageResponse<UserActionLogResp> queryLogs(UserActionLogQueryReq request) {
        if (!elasticsearchLogService.isEnabled()) {
            logger.warn("Elasticsearch未启用，无法查询日志");
            return new PageResponse<>(new ArrayList<>(), 0L, request.getPage(), request.getPageSize());
        }
        
        try {
            ElasticsearchLogService.SearchResult searchResult = elasticsearchLogService.searchLogs(
                    request.getUserId(),
                    request.getUsername(),
                    request.getModule(),
                    request.getActionType(),
                    request.getResult(),
                    request.getStartTime(),
                    request.getEndTime(),
                    request.getPage(),
                    request.getPageSize()
            );

            List<UserActionLogResp> content = searchResult.getDocuments().stream()
                    .map(this::convertDocumentToResp)
                    .collect(Collectors.toList());

            return new PageResponse<>(
                    content,
                    searchResult.getTotal(),
                    request.getPage(),
                    request.getPageSize()
            );
        } catch (Exception e) {
            logger.error("从Elasticsearch查询日志失败", e);
            return new PageResponse<>(new ArrayList<>(), 0L, request.getPage(), request.getPageSize());
        }
    }

    /**
     * 根据ID查询日志详情
     * 注意：由于使用Elasticsearch，此方法返回null，前端应使用列表数据中的完整信息
     */
    @Override
    public UserActionLogResp getLogById(Long id) {
        logger.warn("Elasticsearch模式不支持按ID查询详情，建议前端在列表中展示完整信息");
        return null;
    }

    /**
     * 删除日志（Elasticsearch不支持）
     */
    @Override
    public void deleteLog(Long id) {
        throw new UnsupportedOperationException("Elasticsearch模式不支持删除单条日志");
    }

    /**
     * 批量删除日志（Elasticsearch不支持）
     */
    @Override
    public void batchDeleteLogs(List<Long> ids) {
        throw new UnsupportedOperationException("Elasticsearch模式不支持批量删除日志");
    }

    /**
     * 获取所有操作类型选项（用于下拉菜单）
     */
    @Override
    public java.util.List<String> getActionTypes() {
        if (!elasticsearchLogService.isEnabled()) {
            logger.warn("Elasticsearch未启用，无法获取操作类型");
            return new ArrayList<>();
        }
        
        try {
            return elasticsearchLogService.getActionTypes();
        } catch (Exception e) {
            logger.error("获取操作类型失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public java.util.List<String> getModules() {
        if (!elasticsearchLogService.isEnabled()) {
            logger.warn("Elasticsearch未启用，无法获取操作模块");
            return new ArrayList<>();
        }
        try {
            return elasticsearchLogService.getModules();
        } catch (Exception e) {
            logger.error("获取操作模块失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 将文档转换为响应对象
     */
    private UserActionLogResp convertDocumentToResp(UserActionLogDocument document) {
        UserActionLogResp resp = new UserActionLogResp();
        // Elasticsearch使用UUID字符串作为ID，无法转换为Long，设置为null
        resp.setId(null);
        resp.setUserId(document.getUserId());
        resp.setUsername(document.getUsername());
        resp.setModule(document.getModule());
        resp.setActionType(document.getActionType());
        resp.setDescription(document.getDescription());
        resp.setMethod(document.getMethod());
        resp.setRequestPath(document.getRequestPath());
        resp.setRequestParams(document.getRequestParams());
        resp.setResult(document.getResult());
        resp.setErrorMsg(document.getErrorMsg());
        resp.setIpAddress(document.getIpAddress());
        resp.setUserAgent(document.getUserAgent());
        resp.setExecutionTime(document.getExecutionTime());
        resp.setCreateTime(document.getCreateTime());
        return resp;
    }

    /**
     * 将实体转换为Elasticsearch文档
     */
    private UserActionLogDocument convertToDocument(UserActionLog log) {
        UserActionLogDocument document = new UserActionLogDocument();
        if (log.getId() != null) {
            document.setId(String.valueOf(log.getId()));
        }
        document.setUserId(log.getUserId());
        document.setUsername(log.getUsername());
        document.setModule(log.getModule());
        document.setActionType(log.getActionType());
        document.setDescription(log.getDescription());
        document.setMethod(log.getMethod());
        document.setRequestPath(log.getRequestPath());
        document.setRequestParams(log.getRequestParams());
        document.setResult(log.getResult());
        document.setErrorMsg(log.getErrorMsg());
        document.setIpAddress(log.getIpAddress());
        document.setUserAgent(log.getUserAgent());
        document.setExecutionTime(log.getExecutionTime());
        document.setCreateTime(log.getCreateTime());
        return document;
    }
}
