package com.km.controller.support;

import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RequestUserResolver 单元测试。
 * 覆盖：userId 解析、空值校验、格式校验、非正整数校验。
 */
class RequestUserResolverTest {

    private RequestUserResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RequestUserResolver();
    }

    @Test
    void shouldParseValidUserId() {
        Long userId = resolver.requireUserId("12345");
        assertEquals(12345L, userId);
    }

    @Test
    void shouldParseUserIdWithWhitespace() {
        Long userId = resolver.requireUserId("  42  ");
        assertEquals(42L, userId);
    }

    @Test
    void shouldParseMaxLongUserId() {
        String maxLong = String.valueOf(Long.MAX_VALUE);
        Long userId = resolver.requireUserId(maxLong);
        assertEquals(Long.MAX_VALUE, userId.longValue());
    }

    @Test
    void shouldParseUserIdOne() {
        Long userId = resolver.requireUserId("1");
        assertEquals(1L, userId);
    }

    // ====== null / empty / blank ======

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void shouldThrowExceptionWhenHeaderIsNullOrBlank(String headerValue) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> resolver.requireUserId(headerValue));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("Missing required header"));
    }

    // ====== 格式错误 ======

    @Test
    void shouldThrowExceptionWhenNotANumber() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> resolver.requireUserId("abc"));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("positive integer"));
    }

    @Test
    void shouldThrowExceptionWhenDecimalNumber() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> resolver.requireUserId("3.14"));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void shouldThrowExceptionWhenMixedContent() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> resolver.requireUserId("123abc"));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ====== 非正整数 ======

    @Test
    void shouldThrowExceptionWhenZero() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> resolver.requireUserId("0"));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("positive integer"));
    }

    @Test
    void shouldThrowExceptionWhenNegative() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> resolver.requireUserId("-5"));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    @Test
    void shouldThrowExceptionWhenVeryLargeNumberOverflows() {
        // 超出 Long 范围
        String hugeNumber = "99999999999999999999";
        BusinessException ex = assertThrows(BusinessException.class,
                () -> resolver.requireUserId(hugeNumber));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }
}
