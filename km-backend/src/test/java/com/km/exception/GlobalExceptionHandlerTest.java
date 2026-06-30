package com.km.exception;

import com.km.common.dto.ApiResponse;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalExceptionHandler 单元测试。
 * 覆盖：业务异常、HTTP方法不支持、通用异常的 handler 方法。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ====== BusinessException 测试 ======

    @Test
    void shouldHandleBusinessException() {
        BusinessException ex = new BusinessException(ErrorCode.KM_KB_001);

        ApiResponse<Void> response = handler.handleBusiness(ex);

        assertNotNull(response);
        assertEquals(1002001, response.getCode());
        assertEquals("知识库不存在", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void shouldHandleBusinessExceptionWithCustomMessage() {
        BusinessException ex = new BusinessException(ErrorCode.KM_KB_001, "自定义");

        ApiResponse<Void> response = handler.handleBusiness(ex);

        assertNotNull(response);
        assertEquals(1002001, response.getCode());
        assertEquals("自定义", response.getMessage());
        assertNull(response.getData());
    }

    // ====== HttpRequestMethodNotSupportedException 测试 ======

    @Test
    void shouldHandleMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("POST", new String[]{"GET", "PUT"});

        ApiResponse<Void> response = handler.handleMethodNotSupported(ex);

        assertNotNull(response);
        assertEquals(400, response.getCode());
        assertTrue(response.getMessage().contains("GET"));
        assertTrue(response.getMessage().contains("PUT"));
        assertTrue(response.getMessage().contains("POST") || response.getMessage().contains("请求方法不支持"));
        assertNull(response.getData());
    }

    // ====== 通用 Exception 测试 ======

    @Test
    void shouldHandleGenericException() {
        Exception ex = new RuntimeException("oops");

        ApiResponse<Void> response = handler.handleException(ex);

        assertNotNull(response);
        assertEquals(500, response.getCode());
        assertEquals("服务器内部错误", response.getMessage());
        assertNull(response.getData());
    }

    // ====== 返回格式验证 ======

    @Test
    void shouldReturnApiResponseFormatForBusinessException() {
        BusinessException ex = new BusinessException(ErrorCode.BAD_REQUEST);

        ApiResponse<Void> response = handler.handleBusiness(ex);

        assertEquals(400, response.getCode());
        assertEquals("请求参数错误", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void shouldReturnApiResponseFormatForGenericException() {
        Exception ex = new NullPointerException("Unexpected null");

        ApiResponse<Void> response = handler.handleException(ex);

        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), response.getCode());
        assertEquals(ErrorCode.INTERNAL_ERROR.getMessage(), response.getMessage());
        assertNull(response.getData());
    }
}
