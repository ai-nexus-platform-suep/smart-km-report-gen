package com.km.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonUtils 单元测试。
 * 覆盖：序列化、反序列化、获取 Mapper、序列化失败、反序列化失败、null 值处理。
 */
class JsonUtilsTest {

    private static class SelfReferencing {
        private SelfReferencing self;

        public SelfReferencing getSelf() {
            return self;
        }

        public void setSelf(SelfReferencing self) {
            this.self = self;
        }
    }

    @Test
    void shouldSerializeObjectToJson() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "test");
        map.put("value", 123);

        String json = JsonUtils.toJson(map);

        assertNotNull(json);
        assertTrue(json.contains("\"name\":\"test\""));
        assertTrue(json.contains("\"value\":123"));
    }

    @Test
    void shouldDeserializeJsonToObject() {
        String json = "{\"name\":\"test\",\"value\":123}";

        @SuppressWarnings("unchecked")
        Map<String, Object> map = JsonUtils.fromJson(json, Map.class);

        assertNotNull(map);
        assertEquals("test", map.get("name"));
        assertEquals(123, map.get("value"));
    }

    @Test
    void shouldReturnMapper() {
        ObjectMapper mapper = JsonUtils.getMapper();

        assertNotNull(mapper);
    }

    @Test
    void shouldThrowExceptionOnSerializeFailure() {
        SelfReferencing obj = new SelfReferencing();
        obj.setSelf(obj);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> JsonUtils.toJson(obj));
        assertTrue(exception.getMessage().contains("JSON serialize failed"));
    }

    @Test
    void shouldThrowExceptionOnDeserializeFailure() {
        String invalidJson = "not-a-valid-json{";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> JsonUtils.fromJson(invalidJson, Map.class));
        assertTrue(exception.getMessage().contains("JSON deserialize failed"));
    }

    @Test
    void shouldSerializeAndDeserializeNull() {
        String json = JsonUtils.toJson(null);

        assertNotNull(json);
        assertEquals("null", json);

        Object result = JsonUtils.fromJson("null", Object.class);
        assertNull(result);
    }
}
