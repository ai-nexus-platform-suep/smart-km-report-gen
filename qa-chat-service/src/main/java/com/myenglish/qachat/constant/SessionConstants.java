package com.myenglish.qachat.constant;

public final class SessionConstants {

    /** 记录状态：正常 */
    public static final int STATUS_ACTIVE = 1;
    /** 记录状态：已删除 */
    public static final int STATUS_DELETED = 0;

    /** 消息生成中（SSE 流式） */
    public static final int GENERATE_STATUS_GENERATING = 0;
    /** 消息生成完成 */
    public static final int GENERATE_STATUS_COMPLETED = 1;
    /** 消息生成失败 */
    public static final int GENERATE_STATUS_FAILED = 2;

    public static final String DEFAULT_TITLE = "新对话";
    public static final int TITLE_MAX_LENGTH = 50;

    /** 开发阶段默认用户，接入鉴权后移除 */
    public static final long DEFAULT_USER_ID = 1L;

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";

    /** 知识问答意图 */
    public static final String INTENT_KNOWLEDGE_QA = "KNOWLEDGE_QA";

    /** 趋势统计天数 */
    public static final int TREND_DAYS = 30;

    private SessionConstants() {
    }
}
