package com.km.common.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiResponse 单元测试。
 * 覆盖：ok 工厂方法、fail 工厂方法、泛型支持、Lombok getter/setter、无参/全参构造。
 */
class ApiResponseTest {

    @Test
    void shouldCreateOkResponse() {
        String payload = "test-data";

        ApiResponse<String> response = ApiResponse.ok(payload);

        assertNotNull(response);
        assertEquals(0, response.getCode());
        assertEquals("ok", response.getMessage());
        assertEquals("test-data", response.getData());
    }

    @Test
    void shouldCreateOkResponseWithoutData() {
        ApiResponse<Object> response = ApiResponse.ok();

        assertNotNull(response);
        assertEquals(0, response.getCode());
        assertEquals("ok", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void shouldCreateFailResponse() {
        ApiResponse<Object> response = ApiResponse.fail(500, "服务器内部错误");

        assertNotNull(response);
        assertEquals(500, response.getCode());
        assertEquals("服务器内部错误", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void shouldSupportGenericType() {
        ApiResponse<Integer> intResponse = ApiResponse.ok(42);
        assertEquals(Integer.valueOf(42), intResponse.getData());

        ApiResponse<List<String>> listResponse = ApiResponse.ok(Arrays.asList("a", "b", "c"));
        assertEquals(3, listResponse.getData().size());

        ApiResponse<Void> voidResponse = ApiResponse.ok();
        assertNull(voidResponse.getData());
    }

    @Test
    void shouldSetAndGetFields() {
        ApiResponse<String> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData("payload");

        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
        assertEquals("payload", response.getData());
    }

    @Test
    void shouldConstructWithNoArgsConstructor() {
        ApiResponse<Object> response = new ApiResponse<>();

        assertNotNull(response);
        assertEquals(0, response.getCode());
        assertNull(response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void shouldConstructWithAllArgsConstructor() {
        ApiResponse<String> response = new ApiResponse<>(0, "ok", "hello");

        assertEquals(0, response.getCode());
        assertEquals("ok", response.getMessage());
        assertEquals("hello", response.getData());
    }
}
