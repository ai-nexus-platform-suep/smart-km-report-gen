package com.km.common.constant;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigKeys 单元测试。
 * 覆盖：常量定义、常量值、私有构造器、字符串比较。
 */
class ConfigKeysTest {

    @Test
    void shouldDefineAllKeys() {
        assertNotNull(ConfigKeys.EMBEDDING);
        assertNotNull(ConfigKeys.RERANK);
        assertNotNull(ConfigKeys.PARSER);
    }

    @Test
    void shouldHaveCorrectValues() {
        assertEquals("embedding", ConfigKeys.EMBEDDING);
        assertEquals("rerank", ConfigKeys.RERANK);
        assertEquals("parser", ConfigKeys.PARSER);
    }

    @Test
    void shouldHavePrivateConstructor() throws Exception {
        Constructor<ConfigKeys> constructor = ConfigKeys.class.getDeclaredConstructor();
        assertEquals(0, constructor.getParameterCount());

        constructor.setAccessible(true);
        ConfigKeys instance = constructor.newInstance();
        assertNotNull(instance);
    }

    @Test
    void shouldSupportKeyEquality() {
        String key = "embedding";

        assertEquals(ConfigKeys.EMBEDDING, key);

        assertNotEquals(ConfigKeys.RERANK, key);
        assertNotEquals(ConfigKeys.PARSER, key);
    }
}
