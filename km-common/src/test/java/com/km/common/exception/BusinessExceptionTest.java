package com.km.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BusinessException 单元测试。
 * 覆盖：ErrorCode 构造、自定义消息构造、RuntimeException 继承、code 获取、各 ErrorCode 值。
 */
class BusinessExceptionTest {

    @Test
    void shouldCreateWithErrorCode() {
        BusinessException exception = new BusinessException(ErrorCode.KM_KB_001);

        assertEquals(1002001, exception.getCode());
        assertEquals("知识库不存在", exception.getMessage());
    }

    @Test
    void shouldCreateWithCustomMessage() {
        BusinessException exception = new BusinessException(ErrorCode.KM_KB_001, "自定义消息");

        assertEquals(1002001, exception.getCode());
        assertEquals("自定义消息", exception.getMessage());
    }

    @Test
    void shouldExtendRuntimeException() {
        BusinessException exception = new BusinessException(ErrorCode.INTERNAL_ERROR);

        assertTrue(exception instanceof RuntimeException);
    }

    @Test
    void shouldGetCodeFromErrorCode() {
        BusinessException authException = new BusinessException(ErrorCode.UNAUTHORIZED);
        assertEquals(401, authException.getCode());

        BusinessException notFoundException = new BusinessException(ErrorCode.NOT_FOUND);
        assertEquals(404, notFoundException.getCode());

        BusinessException successException = new BusinessException(ErrorCode.SUCCESS);
        assertEquals(0, successException.getCode());
    }

    @Test
    void shouldContainErrorCodeInfo() {
        // SUCCESS
        BusinessException ex1 = new BusinessException(ErrorCode.SUCCESS);
        assertEquals(0, ex1.getCode());
        assertEquals("ok", ex1.getMessage());

        // BAD_REQUEST
        BusinessException ex2 = new BusinessException(ErrorCode.BAD_REQUEST);
        assertEquals(400, ex2.getCode());

        // INTERNAL_ERROR
        BusinessException ex3 = new BusinessException(ErrorCode.INTERNAL_ERROR);
        assertEquals(500, ex3.getCode());

        // KM_DOC_001
        BusinessException ex4 = new BusinessException(ErrorCode.KM_DOC_001);
        assertEquals(1003001, ex4.getCode());

        // KM_SEARCH_001
        BusinessException ex5 = new BusinessException(ErrorCode.KM_SEARCH_001);
        assertEquals(1004001, ex5.getCode());
    }
}
