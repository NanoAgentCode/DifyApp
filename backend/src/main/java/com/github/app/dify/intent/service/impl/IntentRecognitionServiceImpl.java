package com.github.app.dify.intent.service.impl;

import com.github.app.dify.intent.service.IntentRecognitionService;
import com.github.app.dify.memo.resp.MemoResp;
import com.github.app.dify.memo.service.MemoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;

/**
 * Centralizes deterministic intent recognition before any LLM response is generated.
 */
@Service
public class IntentRecognitionServiceImpl implements IntentRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(IntentRecognitionServiceImpl.class);

    @Autowired
    private MemoService memoService;

    @Override
    public boolean hasMemoIntent(String question) {
        if (question == null || question.trim().isEmpty()) {
            return false;
        }
        String q = question.trim();
        return q.contains("备忘录")
                || q.contains("备忘")
                || q.contains("提醒我")
                || q.contains("提醒一下")
                || q.contains("记一下")
                || q.contains("帮我记")
                || q.contains("记到")
                || q.contains("加入提醒")
                || q.contains("添加提醒");
    }

    @Override
    public MemoResp previewMemo(Long userId, String question) {
        if (userId == null || question == null || question.trim().isEmpty()) {
            return null;
        }
        try {
            logger.info("尝试进行意图识别(备忘录): {}", question);
            MemoResp memoResp = memoService.preview(question);
            if (memoResp != null) {
                logger.info("成功识别备忘录候选项: content={}, remindAt={}", memoResp.getContent(), memoResp.getRemindAt());
                return memoResp;
            }
        } catch (Exception e) {
            logger.debug("未识别到备忘录意图或预览失败: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public String buildMemoConfirmationAnswer(MemoResp memo) {
        String remindTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(memo.getRemindAt());
        return "我识别到你想创建备忘录：\"" + memo.getContent() + "\"，提醒时间为 " + remindTime
                + "。请在弹出的确认框中点击“创建”后，我再为你保存。";
    }
}
