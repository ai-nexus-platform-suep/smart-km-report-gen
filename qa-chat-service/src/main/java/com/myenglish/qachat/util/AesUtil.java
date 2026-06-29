package com.myenglish.qachat.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * AES 加解密工具，用于模型 API Key 的加密存储
 */
public final class AesUtil {

    private static final String ALGORITHM = "AES";

    private AesUtil() {
    }

    /**
     * 使用 AES-256 加密
     *
     * @param plainText 明文
     * @param key       密钥（任意长度，内部 SHA-256 哈希为 32 字节）
     * @return Base64 编码的密文
     */
    public static String encrypt(String plainText, String key) {
        try {
            SecretKeySpec keySpec = buildKeySpec(key);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt failed", e);
        }
    }

    /**
     * 使用 AES-256 解密
     *
     * @param cipherText Base64 编码的密文
     * @param key        密钥
     * @return 明文
     */
    public static String decrypt(String cipherText, String key) {
        try {
            SecretKeySpec keySpec = buildKeySpec(key);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES decrypt failed", e);
        }
    }

    /**
     * 对 API Key 脱敏，只显示前4后4位
     *
     * @param apiKey 明文 API Key
     * @return 脱敏后的字符串，如 sk-****abcd
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private static SecretKeySpec buildKeySpec(String key) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha.digest(key.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
