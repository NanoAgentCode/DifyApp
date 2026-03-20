package com.github.app.dify.ops.trace.core;

/**
 * 追踪内容摘要与脱敏。
 */
public interface TraceSanitizer {

    String summarize(Object payload);
}

