package com.myenglish.qacommon.dto;

/**
 * API响应码常量
 */
public class ApiCode {

    /** 请求成功 */
    public static final int SUCCESS = 200;

    /** 请求参数错误 */
    public static final int BAD_REQUEST = 400;

    /** 未登录或登录状态已失效 */
    public static final int UNAUTHORIZED = 401;

    /** 没有操作权限 */
    public static final int FORBIDDEN = 403;

    /** 请求的资源不存在 */
    public static final int NOT_FOUND = 404;

    /** 请求方法不允许（如GET请求访问了POST接口） */
    public static final int METHOD_NOT_ALLOWED = 405;

    /** 请求过于频繁，触发限流 */
    public static final int TOO_MANY_REQUESTS = 429;

    /** 服务器内部错误 */
    public static final int INTERNAL_ERROR = 500;

    /** 服务暂不可用（维护中或过载） */
    public static final int SERVICE_UNAVAILABLE = 503;

    /** 用户不存在 */
    public static final int USER_NOT_FOUND = 1001;

    /** 用户已存在（注册时账号重复） */
    public static final int USER_ALREADY_EXISTS = 1002;

    /** 密码错误或不合法 */
    public static final int INVALID_PASSWORD = 1003;

    /** Token无效或格式错误 */
    public static final int TOKEN_INVALID = 1004;

    /** Token已过期，需要重新登录 */
    public static final int TOKEN_EXPIRED = 1005;

    /** 账号已被禁用或锁定 */
    public static final int ACCOUNT_DISABLED = 1006;

    /** 第三方服务超时 */
    public static final int THIRD_PARTY_TIMEOUT = 4004;

    /** 第三方服务返回异常 */
    public static final int THIRD_PARTY_ERROR = 4005;

    /** 参数校验不通过 */
    public static final int PARAM_VALID_FAILED = 7001;

    /** 必填参数缺失 */
    public static final int PARAM_MISSING = 7002;

    /** 参数值超出允许范围 */
    public static final int PARAM_OUT_OF_RANGE = 7003;

    /** 参数格式不正确 */
    public static final int PARAM_FORMAT_ERROR = 7004;

    /** 数据不存在 */
    public static final int DATA_NOT_FOUND = 7005;

    /** 数据已存在（重复提交） */
    public static final int DATA_ALREADY_EXISTS = 7006;

    private ApiCode() {
        // 工具类，禁止实例化
    }
}
