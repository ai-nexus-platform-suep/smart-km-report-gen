package com.myenglish.qacommon.security;

import java.lang.annotation.*;

/**
 * 接口权限校验（读取 UserContextHolder 中的 permissions）
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    /** 所需权限码，满足任一即可 */
    String[] value();

    /** true=必须同时拥有全部权限 */
    boolean requireAll() default false;
}
