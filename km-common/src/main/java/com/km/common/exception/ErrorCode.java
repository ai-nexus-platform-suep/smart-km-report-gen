package com.km.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 无效"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    KM_AUTH_001(1001001, "用户名或密码错误"),
    KM_AUTH_002(1001002, "用户名已存在"),
    KM_KB_001(1002001, "知识库不存在"),
    KM_DOC_001(1003001, "文档不存在"),
    KM_DOC_002(1003002, "文档处理失败"),
    KM_DOC_003(1003003, "文件格式不支持"),
    KM_DOC_004(1003004, "文件超出大小限制"),
    KM_DOC_005(1003005, "非FAILED状态不可重试"),
    KM_SEARCH_001(1004001, "检索失败"),
    KM_CFG_001(1005001, "系统配置不存在"),
    KM_CFG_002(1005002, "模型配置连通性测试失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
