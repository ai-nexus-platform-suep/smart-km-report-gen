package com.km.common.exception;


public enum ErrorCode {

    // ===== 通用 (0xx) =====
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ===== 认证 (1xx) =====
    INVALID_TOKEN(1004, "Token 无效或已过期"),
    TOKEN_EXPIRED(1005, "Token 已过期"),
    ACCOUNT_DISABLED(1006, "账号已被禁用"),
    INVALID_CREDENTIALS(1007, "用户名或密码错误"),
    DUPLICATE_USERNAME(1008, "用户名已存在"),
    USER_NOT_FOUND(1009, "用户不存在"),
    REGISTER_FAILED(1010, "注册失败"),
    INVALID_USERNAME(1011, "用户名格式不正确（4-50位字母/数字/下划线）"),
    WEAK_PASSWORD(1012, "密码强度不足（至少8位，需包含字母和数字）");
=======
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
    KM_SEARCH_001(1004001, "检索失败");


    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }


    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
=======

}
