package com.github.app.dify.system.util;

/**
 * 技能模板路径常量（相对于 skills/ 目录，不含 .md）
 * 与 SkillLoader 配合使用，避免硬编码路径
 */
public final class SkillPaths {

    private SkillPaths() {
    }

    // ----- chat -----
    /** 智能问答系统提示 */
    public static final String CHAT_SYSTEM_PROMPT = "chat/system_prompt";
    /** 智能问答系统提示降级 */
    public static final String CHAT_SYSTEM_PROMPT_FALLBACK = "chat/system_prompt_fallback";
    /** 视觉能力说明 */
    public static final String CHAT_VISION_CAPABILITY = "chat/vision_capability";
    /** 无视觉能力说明 */
    public static final String CHAT_NO_VISION_CAPABILITY = "chat/no_vision_capability";
    /** 备忘录创建成功提示模板 */
    public static final String CHAT_MEMO_SUCCESS_HINT = "chat/memo_success_hint";
    /** 浏览器检索系统提示模板 */
    public static final String CHAT_BROWSER_SEARCH_SYSTEM = "chat/browser_search_system";

    // ----- common -----
    /** Markdown 格式要求 */
    public static final String COMMON_MARKDOWN_FORMAT = "common/markdown_format";
}
