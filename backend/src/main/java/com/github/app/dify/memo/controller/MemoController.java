package com.github.app.dify.memo.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.memo.req.MemoCreateReq;
import com.github.app.dify.memo.resp.MemoResp;
import com.github.app.dify.memo.service.MemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 备忘录接口：GET/POST /api/memos，GET /api/memos/due，PATCH/DELETE /api/memos/{id}
 */
@Tag(name = "备忘录")
@RestController
@RequestMapping("/api/memos")
public class MemoController extends BaseController {

    @Autowired
    private MemoService memoService;

    @Operation(summary = "获取备忘录列表")
    @GetMapping
    public ResponseEntity<List<MemoResp>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer size,
            HttpServletRequest request
    ) {
        Long userId = getUserId(request);
        List<MemoResp> list = memoService.list(userId, status, page != null ? page : 0, size != null ? size : 50);
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "获取已到期的待提醒列表（轮询弹通知用）")
    @GetMapping("/due")
    public ResponseEntity<List<MemoResp>> listDue(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<MemoResp> list = memoService.listDue(userId);
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "创建备忘录（自然语言）")
    @PostMapping
    public ResponseEntity<MemoResp> create(
            @Valid @RequestBody MemoCreateReq req,
            HttpServletRequest request
    ) {
        Long userId = getUserId(request);
        MemoResp resp = memoService.create(userId, req.getRawInput());
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "标记为已提醒")
    @PatchMapping("/{id}/done")
    public ResponseEntity<Void> markDone(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        Long userId = getUserId(request);
        memoService.markDone(userId, id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "取消备忘录")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        Long userId = getUserId(request);
        memoService.cancel(userId, id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "删除备忘录")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        Long userId = getUserId(request);
        memoService.delete(userId, id);
        return ResponseEntity.ok().build();
    }
}
