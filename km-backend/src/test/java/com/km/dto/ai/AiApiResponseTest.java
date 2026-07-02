package com.km.dto.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiApiResponse 单元测试。
 * 覆盖：泛型数据绑定、code/message 字段。
 */
class AiApiResponseTest {

    @Test
    void shouldSetAndGetCode() {
        AiApiResponse<String> response = new AiApiResponse<>();
        response.setCode(0);
        assertEquals(0, response.getCode());
    }

    @Test
    void shouldSetAndGetMessage() {
        AiApiResponse<String> response = new AiApiResponse<>();
        response.setMessage("success");
        assertEquals("success", response.getMessage());
    }

    @Test
    void shouldSetAndGetData() {
        AiApiResponse<String> response = new AiApiResponse<>();
        response.setData("hello");
        assertEquals("hello", response.getData());
    }

    @Test
    void shouldSupportIntegerData() {
        AiApiResponse<Integer> response = new AiApiResponse<>();
        response.setCode(0);
        response.setMessage("ok");
        response.setData(42);

        assertEquals(0, response.getCode());
        assertEquals("ok", response.getMessage());
        assertEquals(42, response.getData());
    }

    @Test
    void shouldSupportNullData() {
        AiApiResponse<Void> response = new AiApiResponse<>();
        response.setCode(500);
        response.setMessage("Internal Server Error");
        response.setData(null);

        assertEquals(500, response.getCode());
        assertEquals("Internal Server Error", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void shouldSupportNestedObjectData() {
        EmbedResponse embed = new EmbedResponse();
        embed.setDimension(1024);

        AiApiResponse<EmbedResponse> response = new AiApiResponse<>();
        response.setCode(0);
        response.setMessage("ok");
        response.setData(embed);

        assertNotNull(response.getData());
        assertEquals(1024, response.getData().getDimension());
    }
}
