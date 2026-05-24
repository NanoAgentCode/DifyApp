package com.github.app.dify.memo.service.impl;

import com.github.app.dify.common.exception.BusinessException;
import com.github.app.dify.common.exception.ErrorCode;
import com.github.app.dify.common.exception.NotFoundException;
import com.github.app.dify.memo.domain.Memo;
import com.github.app.dify.memo.repository.MemoRepository;
import com.github.app.dify.memo.resp.MemoResp;
import com.github.app.dify.memo.service.MemoService;
import com.github.app.dify.memo.util.MemoTimeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MemoServiceImpl implements MemoService {

    private static final Logger logger = LoggerFactory.getLogger(MemoServiceImpl.class);
    private static final int NOT_DELETED = 0;
    private static final int DEFAULT_PAGE_SIZE = 50;

    @Autowired
    private MemoRepository memoRepository;

    @Override
    public List<MemoResp> list(Long userId, String status, int page, int size) {
        int sizeVal = size > 0 ? Math.min(size, 100) : DEFAULT_PAGE_SIZE;
        PageRequest pr = PageRequest.of(Math.max(0, page), sizeVal);
        List<Memo> list = status != null && !status.trim().isEmpty()
                ? memoRepository.findAllByUserIdAndStatus(userId, status.trim(), pr)
                : memoRepository.findAllByUserId(userId, pr);
        return list.stream().map(this::toResp).collect(Collectors.toList());
    }

    @Override
    public List<MemoResp> listDue(Long userId) {
        List<Memo> list = memoRepository.findDueByUserId(userId, new Date());
        if (!list.isEmpty()) {
            logger.info("用户 {} 有 {} 个待提醒项到期", userId, list.size());
        }
        return list.stream().map(this::toResp).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MemoResp create(Long userId, String rawInput) {
        MemoTimeParser.ParseResult parsed = MemoTimeParser.parse(rawInput);
        if (parsed.getRemindAt() == null) {
            throw new BusinessException("无法识别提醒时间，请使用例如：30分钟后提醒我开会、明天9点提醒吃药", ErrorCode.DATA_VALIDATION_FAILED);
        }
        if (parsed.getRemindAt().before(new Date())) {
            throw new BusinessException("提醒时间不能早于当前时间", ErrorCode.DATA_VALIDATION_FAILED);
        }
        String content = parsed.getContent();
        if (content == null || content.trim().isEmpty()) {
            content = rawInput.trim();
        }

        Memo m = new Memo();
        m.setUserId(userId);
        m.setContent(content.trim());
        m.setRemindAt(parsed.getRemindAt());
        m.setIntervalMinutes(parsed.getIntervalMinutes());
        m.setStatus(Memo.STATUS_PENDING);
        m.setDeleted(NOT_DELETED);
        Date now = new Date();
        m.setCreateTime(now);
        m.setUpdateTime(now);
        m = memoRepository.save(m);
        logger.info("创建备忘录: id={}, content={}, remindAt={}", m.getId(), m.getContent(), m.getRemindAt());
        return toResp(m);
    }

    @Override
    public MemoResp preview(String rawInput) {
        MemoTimeParser.ParseResult parsed = MemoTimeParser.parse(rawInput);
        if (parsed.getRemindAt() == null) {
            throw new BusinessException("Cannot identify reminder time", ErrorCode.DATA_VALIDATION_FAILED);
        }
        if (parsed.getRemindAt().before(new Date())) {
            throw new BusinessException("Reminder time cannot be earlier than current time", ErrorCode.DATA_VALIDATION_FAILED);
        }
        String content = parsed.getContent();
        if (content == null || content.trim().isEmpty()) {
            content = rawInput != null ? rawInput.trim() : "";
        }

        MemoResp r = new MemoResp();
        r.setContent(content.trim());
        r.setRemindAt(parsed.getRemindAt());
        r.setStatus(Memo.STATUS_PENDING);
        r.setIntervalMinutes(parsed.getIntervalMinutes());
        return r;
    }

    @Override
    @Transactional
    public MemoResp createConfirmed(Long userId, String content, Date remindAt, Integer intervalMinutes) {
        if (remindAt == null) {
            throw new BusinessException("Cannot identify reminder time", ErrorCode.DATA_VALIDATION_FAILED);
        }
        if (remindAt.before(new Date())) {
            throw new BusinessException("Reminder time cannot be earlier than current time", ErrorCode.DATA_VALIDATION_FAILED);
        }
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException("Content cannot be empty", ErrorCode.DATA_VALIDATION_FAILED);
        }

        Memo m = new Memo();
        m.setUserId(userId);
        m.setContent(content.trim());
        m.setRemindAt(remindAt);
        m.setIntervalMinutes(intervalMinutes);
        m.setStatus(Memo.STATUS_PENDING);
        m.setDeleted(NOT_DELETED);
        Date now = new Date();
        m.setCreateTime(now);
        m.setUpdateTime(now);
        m = memoRepository.save(m);
        logger.info("鍒涘缓澶囧繕褰? id={}, content={}, remindAt={}", m.getId(), m.getContent(), m.getRemindAt());
        return toResp(m);
    }

    @Override
    @Transactional
    public void markDone(Long userId, Long id) {
        Optional<Memo> opt = memoRepository.findByIdAndUserId(id, userId);
        if (opt.isEmpty()) {
            throw new NotFoundException("备忘录不存在");
        }
        Memo memo = opt.get();
        if (memo.getIntervalMinutes() != null && memo.getIntervalMinutes() > 0) {
            Date next = addMinutes(memo.getRemindAt(), memo.getIntervalMinutes());
            int n = memoRepository.updateRemindAtById(id, next);
            if (n <= 0) {
                throw new NotFoundException("备忘录不存在");
            }
        } else {
            int n = memoRepository.updateStatusByIdAndUserId(id, userId, Memo.STATUS_DONE);
            if (n <= 0) {
                throw new NotFoundException("备忘录不存在");
            }
        }
    }

    private static Date addMinutes(Date date, int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.MINUTE, minutes);
        return cal.getTime();
    }

    @Override
    @Transactional
    public void cancel(Long userId, Long id) {
        int n = memoRepository.updateStatusByIdAndUserId(id, userId, Memo.STATUS_CANCELLED);
        if (n <= 0) {
            throw new NotFoundException("备忘录不存在");
        }
    }

    @Override
    @Transactional
    public void delete(Long userId, Long id) {
        int n = memoRepository.softDeleteByIdAndUserId(id, userId);
        if (n <= 0) {
            throw new NotFoundException("备忘录不存在");
        }
    }

    private MemoResp toResp(Memo m) {
        MemoResp r = new MemoResp();
        r.setId(m.getId());
        r.setContent(m.getContent());
        r.setRemindAt(m.getRemindAt());
        r.setStatus(m.getStatus());
        r.setIntervalMinutes(m.getIntervalMinutes());
        r.setCreateTime(m.getCreateTime());
        r.setUpdateTime(m.getUpdateTime());
        return r;
    }
}
