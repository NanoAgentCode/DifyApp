package com.github.app.dify.memo.controller;

import com.github.app.dify.common.controller.BaseController;
import com.github.app.dify.memo.req.MemoConfirmReq;
import com.github.app.dify.memo.req.MemoCreateReq;
import com.github.app.dify.memo.resp.MemoResp;
import com.github.app.dify.memo.service.MemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Memo")
@RestController
@RequestMapping("/api/memos")
public class MemoController extends BaseController {

    @Autowired
    private MemoService memoService;

    @Operation(summary = "List memos")
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

    @Operation(summary = "List due memos")
    @GetMapping("/due")
    public ResponseEntity<List<MemoResp>> listDue(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<MemoResp> list = memoService.listDue(userId);
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "Create memo from natural language")
    @PostMapping
    public ResponseEntity<MemoResp> create(
            @Valid @RequestBody MemoCreateReq req,
            HttpServletRequest request
    ) {
        Long userId = getUserId(request);
        MemoResp resp = memoService.create(userId, req.getRawInput());
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Create memo after user confirmation")
    @PostMapping("/confirmed")
    public ResponseEntity<MemoResp> createConfirmed(
            @Valid @RequestBody MemoConfirmReq req,
            HttpServletRequest request
    ) {
        Long userId = getUserId(request);
        MemoResp resp = memoService.createConfirmed(
                userId,
                req.getContent(),
                req.getRemindAt(),
                req.getIntervalMinutes());
        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Mark memo done")
    @PatchMapping("/{id}/done")
    public ResponseEntity<Void> markDone(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        Long userId = getUserId(request);
        memoService.markDone(userId, id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Cancel memo")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable Long id,
            HttpServletRequest request
    ) {
        Long userId = getUserId(request);
        memoService.cancel(userId, id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete memo")
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
