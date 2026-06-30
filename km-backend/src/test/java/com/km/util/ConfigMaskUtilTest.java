package com.km.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigMaskUtil 单元测试。
 * 覆盖：API Key 脱敏逻辑、已脱敏判断、空值边界。
 */
class ConfigMaskUtilTest {

    // ====== maskApiKey 测试 ======

    @Test
    void shouldReturnEmptyForNull() {
        assertEquals("", ConfigMaskUtil.maskApiKey(null));
    }

    @Test
    void shouldReturnEmptyForEmptyString() {
        assertEquals("", ConfigMaskUtil.maskApiKey(""));
    }

    @Test
    void shouldReturnMaskForShortKey() {
        assertEquals("****", ConfigMaskUtil.maskApiKey("abc"));
    }

    @Test
    void shouldReturnMaskFor8CharKey() {
        // boundary: length == 8
        assertEquals("****", ConfigMaskUtil.maskApiKey("12345678"));
    }

    @Test
    void shouldMaskLongKeyStandard() {
        // prefix up to first '-' char, suffix = last 4 chars
        String result = ConfigMaskUtil.maskApiKey("sk-abcdefghijklmnopqrstuvwxyz123456");
        assertEquals("sk-****3456", result);
    }

    @Test
    void shouldMaskLongKeyNoDash() {
        // no dash -> prefix = first 3 chars
        String result = ConfigMaskUtil.maskApiKey("sk1234567890abcdef");
        assertEquals("sk1****cdef", result);
    }

    @Test
    void shouldMaskVeryLongKey() {
        String result = ConfigMaskUtil.maskApiKey("abcdefghijklmnopqrstuvwxyz");
        assertEquals("abc****wxyz", result);
    }

    // ====== isMaskedKey 测试 ======

    @Test
    void shouldIdentifyMaskedKey() {
        assertTrue(ConfigMaskUtil.isMaskedKey("sk-****abcd"));
    }

    @Test
    void shouldIdentifyNonMaskedKey() {
        assertFalse(ConfigMaskUtil.isMaskedKey("sk-real-key"));
    }

    @Test
    void shouldIdentifyNullAsNotMasked() {
        assertFalse(ConfigMaskUtil.isMaskedKey(null));
    }

    @Test
    void shouldIdentifyEmptyAsNotMasked() {
        assertFalse(ConfigMaskUtil.isMaskedKey(""));
    }

    // ====== 私有构造函数测试 ======

    @Test
    void shouldHavePrivateConstructor() throws Exception {
        Constructor<ConfigMaskUtil> constructor = ConfigMaskUtil.class.getDeclaredConstructor();
        assertFalse(constructor.isAccessible());
        constructor.setAccessible(true);
        ConfigMaskUtil instance = constructor.newInstance();
        assertNotNull(instance);
    }
}
