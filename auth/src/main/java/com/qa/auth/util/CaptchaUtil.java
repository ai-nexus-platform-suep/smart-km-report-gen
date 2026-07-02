package com.qa.auth.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 图形验证码生成工具 - 纯 Java AWT 实现，无外部依赖
 */
public final class CaptchaUtil {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int WIDTH = 130;
    private static final int HEIGHT = 48;
    private static final int FONT_SIZE = 32;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private CaptchaUtil() {}

    /**
     * 生成随机验证码字符串
     */
    public static String generateCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 生成验证码图片
     */
    public static BufferedImage generateImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 白色背景
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 干扰线
        g.setColor(randomColor(150, 200));
        for (int i = 0; i < 8; i++) {
            int x1 = RANDOM.nextInt(WIDTH);
            int y1 = RANDOM.nextInt(HEIGHT);
            int x2 = RANDOM.nextInt(WIDTH);
            int y2 = RANDOM.nextInt(HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }

        // 干扰点
        for (int i = 0; i < 80; i++) {
            g.setColor(randomColor(150, 200));
            g.fillOval(RANDOM.nextInt(WIDTH), RANDOM.nextInt(HEIGHT), 2, 2);
        }

        // 绘制字符
        Font[] fonts = {
                new Font("Arial", Font.BOLD, FONT_SIZE),
                new Font("Arial", Font.ITALIC, FONT_SIZE),
                new Font("Courier New", Font.BOLD, FONT_SIZE),
        };

        int x = 15;
        for (int i = 0; i < code.length(); i++) {
            g.setFont(fonts[RANDOM.nextInt(fonts.length)]);
            g.setColor(randomColor(20, 120));

            // 字符旋转
            double angle = (RANDOM.nextDouble() - 0.5) * 0.5;
            g.rotate(angle, x + FONT_SIZE / 2.0, HEIGHT / 2.0 + 5);

            g.drawString(String.valueOf(code.charAt(i)), x, HEIGHT / 2 + 12);

            g.rotate(-angle, x + FONT_SIZE / 2.0, HEIGHT / 2.0 + 5);
            x += 28;
        }

        g.dispose();
        return image;
    }

    /**
     * 将图片转为 base64 字符串
     */
    public static String toBase64(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("验证码图片生成失败", e);
        }
    }

    private static Color randomColor(int min, int max) {
        int r = min + RANDOM.nextInt(max - min);
        int g = min + RANDOM.nextInt(max - min);
        int b = min + RANDOM.nextInt(max - min);
        return new Color(r, g, b);
    }
}
