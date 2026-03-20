package com.github.app.dify.ops.userlog.controller;

import com.github.app.dify.common.resp.PageResponse;
import com.github.app.dify.ops.userlog.req.UserActionLogQueryReq;
import com.github.app.dify.ops.userlog.resp.UserActionLogResp;
import com.github.app.dify.ops.userlog.service.UserActionLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 用户行为日志Controller（管理端）
 */
@Tag(name = "用户行为日志管理")
@RestController
@RequestMapping("/api/admin/user-action-logs")
public class UserActionLogController {

    private static final Logger logger = LoggerFactory.getLogger(UserActionLogController.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private UserActionLogService userActionLogService;

    /**
     * 分页查询用户行为日志
     */
    @Operation(summary = "分页查询用户行为日志")
    @GetMapping
    public ResponseEntity<PageResponse<UserActionLogResp>> queryLogs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            UserActionLogQueryReq request = new UserActionLogQueryReq();
            request.setUserId(userId);
            request.setUsername(username);
            request.setModule(module);
            request.setActionType(actionType);
            request.setResult(result);
            
            // 解析时间参数
            if (startTime != null && !startTime.isEmpty()) {
                request.setStartTime(LocalDateTime.parse(startTime, TIME_FORMATTER));
            }
            if (endTime != null && !endTime.isEmpty()) {
                request.setEndTime(LocalDateTime.parse(endTime, TIME_FORMATTER));
            }
            
            request.setPage(page);
            request.setPageSize(pageSize);

            PageResponse<UserActionLogResp> response = userActionLogService.queryLogs(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询用户行为日志失败", e);
            throw e;
        }
    }

    /**
     * 根据ID查询日志详情
     */
    @Operation(summary = "查询日志详情")
    @GetMapping("/{id}")
    public ResponseEntity<UserActionLogResp> getLogById(@PathVariable Long id) {
        try {
            UserActionLogResp response = userActionLogService.getLogById(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询日志详情失败: id={}", id, e);
            throw e;
        }
    }

    /**
     * 删除日志
     */
    @Operation(summary = "删除日志")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLog(@PathVariable Long id) {
        try {
            userActionLogService.deleteLog(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("删除日志失败: id={}", id, e);
            throw e;
        }
    }

    /**
     * 批量删除日志
     */
    @Operation(summary = "批量删除日志")
    @DeleteMapping("/batch")
    public ResponseEntity<Void> batchDeleteLogs(@RequestBody List<Long> ids) {
        try {
            userActionLogService.batchDeleteLogs(ids);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            logger.error("批量删除日志失败", e);
            throw e;
        }
    }

    /**
     * 获取操作类型选项（用于下拉菜单）
     */
    @Operation(summary = "获取操作类型选项")
    @GetMapping("/action-types")
    public ResponseEntity<List<String>> getActionTypes() {
        try {
            List<String> actionTypes = userActionLogService.getActionTypes();
            logger.info("返回操作类型列表: {}", actionTypes);
            return ResponseEntity.ok(actionTypes);
        } catch (Exception e) {
            logger.error("获取操作类型选项失败", e);
            throw e;
        }
    }

    /**
     * 获取操作模块选项（用于下拉菜单）
     */
    @Operation(summary = "获取操作模块选项")
    @GetMapping("/modules")
    public ResponseEntity<List<String>> getModules() {
        try {
            List<String> modules = userActionLogService.getModules();
            logger.info("返回操作模块列表: {}", modules);
            return ResponseEntity.ok(modules);
        } catch (Exception e) {
            logger.error("获取操作模块选项失败", e);
            throw e;
        }
    }
}
