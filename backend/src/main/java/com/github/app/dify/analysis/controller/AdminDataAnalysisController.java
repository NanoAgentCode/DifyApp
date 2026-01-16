package com.github.app.dify.analysis.controller;

import com.github.app.dify.analysis.req.DataAnalysisSettingsReq;
import com.github.app.dify.analysis.resp.DataAnalysisSettingsResp;
import com.github.app.dify.analysis.resp.DataAnalysisStatusResp;
import com.github.app.dify.analysis.resp.GraphViewResp;
import com.github.app.dify.analysis.service.DataAnalysisService;
import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.common.exception.ForbiddenException;
import com.github.app.dify.userlog.annotation.UserAction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "数据分析")
@RestController
@RequestMapping("/api/admin/data-analysis")
public class AdminDataAnalysisController extends BaseController {

    @Autowired
    private DataAnalysisService dataAnalysisService;

    private void checkAdmin(HttpServletRequest request) {
        Object roleObj = request.getAttribute("role");
        if (!(roleObj instanceof Integer) || (Integer) roleObj != 1) {
            throw new ForbiddenException("需要管理员权限");
        }
    }

    @Operation(summary = "获取数据分析同步配置")
    @GetMapping("/settings")
    public ResponseEntity<DataAnalysisSettingsResp> getSettings(HttpServletRequest request) {
        checkAdmin(request);
        return ResponseEntity.ok(dataAnalysisService.getSettings());
    }

    @UserAction(module = "数据分析", actionType = "更新配置", description = "更新数据分析同步配置")
    @Operation(summary = "更新数据分析同步配置")
    @PutMapping("/settings")
    public ResponseEntity<DataAnalysisSettingsResp> updateSettings(
            @Validated @RequestBody DataAnalysisSettingsReq req,
            HttpServletRequest request) {
        checkAdmin(request);
        Long userId = getUserId(request);
        String username = getUsername(request);
        return ResponseEntity.ok(dataAnalysisService.updateSettings(req, userId, username));
    }

    @Operation(summary = "获取数据分析同步状态")
    @GetMapping("/status")
    public ResponseEntity<DataAnalysisStatusResp> getStatus(HttpServletRequest request) {
        checkAdmin(request);
        return ResponseEntity.ok(dataAnalysisService.getStatus());
    }

    @UserAction(module = "数据分析", actionType = "立即同步", description = "立即执行数据同步到Neo4j")
    @Operation(summary = "立即执行数据同步")
    @PostMapping("/run")
    public ResponseEntity<Void> runNow(HttpServletRequest request) {
        checkAdmin(request);
        Long userId = getUserId(request);
        String username = getUsername(request);
        dataAnalysisService.triggerRun(userId, username);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "获取图数据视图")
    @GetMapping("/graph")
    public ResponseEntity<GraphViewResp> getGraphView(
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        checkAdmin(request);
        return ResponseEntity.ok(dataAnalysisService.getGraphView(limit));
    }
}
