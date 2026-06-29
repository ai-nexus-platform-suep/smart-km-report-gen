package com.km.util;

public final class ConfigMaskUtil {

    private static final String MASK_TOKEN = "****";

    private ConfigMaskUtil() {
    }

    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return MASK_TOKEN;
        }
        String suffix = apiKey.substring(apiKey.length() - 4);
        int prefixLen = Math.min(3, apiKey.indexOf('-') >= 0 ? apiKey.indexOf('-') + 1 : 3);
        String prefix = apiKey.substring(0, prefixLen);
        return prefix + MASK_TOKEN + suffix;
    }

    public static boolean isMaskedKey(String input) {
        return input != null && input.contains(MASK_TOKEN);
    }
}
