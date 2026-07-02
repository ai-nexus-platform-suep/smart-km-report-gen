package com.km.common.exception;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ErrorCode 单元测试。
 * 覆盖：code/message 值验证、code 唯一性、valueOf、枚举总数、模块前缀区分、getter。
 */
class ErrorCodeTest {

    @Test
    void shouldHaveCorrectCodeAndMessage() {
        assertEquals(0, ErrorCode.SUCCESS.getCode());
        assertEquals("ok", ErrorCode.SUCCESS.getMessage());

        assertEquals(400, ErrorCode.BAD_REQUEST.getCode());
        assertEquals("请求参数错误", ErrorCode.BAD_REQUEST.getMessage());

        assertEquals(401, ErrorCode.UNAUTHORIZED.getCode());
        assertEquals("未登录或 Token 无效", ErrorCode.UNAUTHORIZED.getMessage());

        assertEquals(403, ErrorCode.FORBIDDEN.getCode());
        assertEquals("无权限", ErrorCode.FORBIDDEN.getMessage());

        assertEquals(404, ErrorCode.NOT_FOUND.getCode());
        assertEquals("资源不存在", ErrorCode.NOT_FOUND.getMessage());

        assertEquals(500, ErrorCode.INTERNAL_ERROR.getCode());
        assertEquals("服务器内部错误", ErrorCode.INTERNAL_ERROR.getMessage());
    }

    @Test
    void shouldHaveUniqueCodes() {
        ErrorCode[] values = ErrorCode.values();
        Set<Integer> codeSet = new HashSet<>();
        for (ErrorCode ec : values) {
            assertFalse(codeSet.contains(ec.getCode()),
                    "Duplicate code found: " + ec.getCode());
            codeSet.add(ec.getCode());
        }
    }

    @Test
    void shouldBeAccessibleByValueOf() {
        ErrorCode success = ErrorCode.valueOf("SUCCESS");
        assertNotNull(success);
        assertEquals(0, success.getCode());

        ErrorCode notFound = ErrorCode.valueOf("NOT_FOUND");
        assertNotNull(notFound);
        assertEquals(404, notFound.getCode());
    }

    @Test
    void shouldHaveCorrectCount() {
        assertEquals(17, ErrorCode.values().length);
    }

    @Test
    void shouldDistinguishModuleCodes() {
        // 通用 HTTP 状态码: 4xx, 5xx 范围
        assertEquals(400, ErrorCode.BAD_REQUEST.getCode());
        assertEquals(401, ErrorCode.UNAUTHORIZED.getCode());
        assertEquals(403, ErrorCode.FORBIDDEN.getCode());
        assertEquals(404, ErrorCode.NOT_FOUND.getCode());
        assertEquals(500, ErrorCode.INTERNAL_ERROR.getCode());

        // AUTH 模块: 1001xxx
        assertEquals(1001001, ErrorCode.KM_AUTH_001.getCode());
        assertEquals(1001002, ErrorCode.KM_AUTH_002.getCode());

        // KB 模块: 1002xxx
        assertEquals(1002001, ErrorCode.KM_KB_001.getCode());

        // DOC 模块: 1003xxx
        assertEquals(1003001, ErrorCode.KM_DOC_001.getCode());
        assertEquals(1003002, ErrorCode.KM_DOC_002.getCode());
        assertEquals(1003003, ErrorCode.KM_DOC_003.getCode());
        assertEquals(1003004, ErrorCode.KM_DOC_004.getCode());
        assertEquals(1003005, ErrorCode.KM_DOC_005.getCode());

        // SEARCH 模块: 1004xxx
        assertEquals(1004001, ErrorCode.KM_SEARCH_001.getCode());

        // CFG 模块: 1005xxx
        assertEquals(1005001, ErrorCode.KM_CFG_001.getCode());
    }

    @Test
    void shouldGetCode() {
        assertEquals(1002001, ErrorCode.KM_KB_001.getCode());
    }

    @Test
    void shouldGetMessage() {
        assertEquals("知识库不存在", ErrorCode.KM_KB_001.getMessage());
    }
}
