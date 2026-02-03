package com.github.app.dify.prompt.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.prompt.req.PromptCreateReq;
import com.github.app.dify.prompt.req.PromptUpdateReq;
import com.github.app.dify.prompt.resp.PromptResp;
import com.github.app.dify.prompt.service.PromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提示词管理接口
 * GET/POST /api/prompts、GET/PUT/DELETE /api/prompts/{id}
 */
@Tag(name = "提示词管理")
@RestController
@RequestMapping("/api/prompts")
public class PromptController extends BaseController {

    @Autowired
    private PromptService promptService;

    @Operation(summary = "获取提示词列表")
    @GetMapping
    public ResponseEntity<List<PromptResp>> list(
            @RequestParam(required = false) String keyword,
            HttpServletRequest request
    ) {
        getUserId(request);
        List<PromptResp> list = promptService.list(keyword);
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "根据ID获取提示词")
    @GetMapping("/{id}")
    public ResponseEntity<PromptResp> getById(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        getUserId(request);
        PromptResp resp = promptService.getById(id);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "创建提示词")
    @PostMapping
    public ResponseEntity<PromptResp> create(
            @Valid @RequestBody PromptCreateReq req,
            HttpServletRequest request
    ) {
        getUserId(request);
        PromptResp resp = promptService.create(req);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "更新提示词")
    @PutMapping("/{id}")
    public ResponseEntity<PromptResp> update(
            @PathVariable Long id,
            @Valid @RequestBody PromptUpdateReq req,
            HttpServletRequest request
    ) {
        getUserId(request);
        PromptResp resp = promptService.update(id, req);
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "删除提示词")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        getUserId(request);
        promptService.delete(id);
        return ResponseEntity.ok().build();
    }
}
