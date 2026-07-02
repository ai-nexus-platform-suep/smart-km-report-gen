package com.qa.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 强密码校验注解
 * 要求：至少8位，包含大写字母、小写字母、数字、特殊字符中至少3种
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "密码强度不足，需至少8位且包含大写字母、小写字母、数字、特殊字符中的至少3种";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
