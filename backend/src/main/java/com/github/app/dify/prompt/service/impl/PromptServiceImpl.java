package com.github.app.dify.prompt.service.impl;

import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.prompt.domain.Prompt;
import com.github.app.dify.prompt.repository.PromptRepository;
import com.github.app.dify.prompt.req.PromptCreateReq;
import com.github.app.dify.prompt.req.PromptUpdateReq;
import com.github.app.dify.prompt.resp.PromptResp;
import com.github.app.dify.prompt.service.PromptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PromptServiceImpl implements PromptService {

    private static final int NOT_DELETED = 0;

    @Autowired
    private PromptRepository promptRepository;

    @Override
    public List<PromptResp> list(String keyword) {
        String k = (keyword != null && keyword.trim().isEmpty()) ? null : (keyword != null ? keyword.trim() : null);
        List<Prompt> list = promptRepository.findAllActiveByKeyword(k);
        return list.stream().map(this::toResp).collect(Collectors.toList());
    }

    @Override
    public PromptResp getById(Long id) {
        Prompt p = promptRepository.findActiveById(id)
                .orElseThrow(() -> new NotFoundException("提示词不存在"));
        return toResp(p);
    }

    @Override
    @Transactional
    public PromptResp create(PromptCreateReq req) {
        Prompt p = new Prompt();
        p.setTitle(req.getTitle().trim());
        p.setContent(req.getContent() != null ? req.getContent().trim() : "");
        p.setDeleted(NOT_DELETED);
        Date now = new Date();
        p.setCreateTime(now);
        p.setUpdateTime(now);
        p = promptRepository.save(p);
        return toResp(p);
    }

    @Override
    @Transactional
    public PromptResp update(Long id, PromptUpdateReq req) {
        Prompt p = promptRepository.findActiveById(id)
                .orElseThrow(() -> new NotFoundException("提示词不存在"));
        p.setTitle(req.getTitle().trim());
        p.setContent(req.getContent() != null ? req.getContent().trim() : "");
        p.setUpdateTime(new Date());
        p = promptRepository.save(p);
        return toResp(p);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        int updated = promptRepository.softDeleteById(id);
        if (updated <= 0) {
            throw new NotFoundException("提示词不存在");
        }
    }

    private PromptResp toResp(Prompt p) {
        PromptResp r = new PromptResp();
        r.setId(p.getId());
        r.setTitle(p.getTitle());
        r.setContent(p.getContent());
        r.setCreateTime(p.getCreateTime());
        r.setUpdateTime(p.getUpdateTime());
        return r;
    }
}
