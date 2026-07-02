package com.qa.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;
import java.util.regex.Pattern;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?`~]");

    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "password", "12345678", "123456789", "qwerty123", "admin123",
            "abc12345", "11111111", "aaaaaaaa", "Password1", "Pa$$w0rd"
    );

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("密码不能为空")
                    .addConstraintViolation();
            return false;
        }

        if (password.length() < 8) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("密码长度不能少于8位")
                    .addConstraintViolation();
            return false;
        }

        // 检查弱密码黑名单
        if (WEAK_PASSWORDS.contains(password.toLowerCase())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("密码过于简单，请使用更复杂的密码")
                    .addConstraintViolation();
            return false;
        }

        // 统计字符类型满足情况
        int types = 0;
        if (UPPER.matcher(password).find()) types++;
        if (LOWER.matcher(password).find()) types++;
        if (DIGIT.matcher(password).find()) types++;
        if (SPECIAL.matcher(password).find()) types++;

        if (types < 3) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("密码需包含大写字母、小写字母、数字、特殊字符中的至少3种")
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}
