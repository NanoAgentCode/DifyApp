package com.github.app.dify.intent.service;

import com.github.app.dify.memo.resp.MemoResp;

/**
 * Intent recognition facade used before model generation.
 */
public interface IntentRecognitionService {

    boolean hasMemoIntent(String question);

    MemoResp previewMemo(Long userId, String question);

    String buildMemoConfirmationAnswer(MemoResp memo);
}
