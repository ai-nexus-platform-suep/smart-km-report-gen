package com.qa.auth.util;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;

/**
 * 纯 Java AWT 生成图形验证码
 */
public final class CaptchaUtil {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int WIDTH = 130;
    private static final int HEIGHT = 48;
    private static final int CODE_LEN = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CaptchaUtil() {}

    public record CaptchaImage(BufferedImage image, String code) {}

    public static CaptchaImage generate() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setColor(new Color(240, 240, 240));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        StringBuilder code = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            code.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }

        g.setFont(new Font("Arial", Font.BOLD, 28));
        for (int i = 0; i < CODE_LEN; i++) {
            g.setColor(randomColor());
            g.drawString(String.valueOf(code.charAt(i)), 20 + i * 25, 28 + RANDOM.nextInt(10));
        }

        // 干扰线
        g.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < 4; i++) {
            g.setColor(randomColor());
            g.drawLine(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT),
                    RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT));
        }

        g.dispose();
        return new CaptchaImage(image, code.toString());
    }

    private static Color randomColor() {
        return new Color(20 + RANDOM.nextInt(100), 20 + RANDOM.nextInt(100), 20 + RANDOM.nextInt(100));
    }
}
