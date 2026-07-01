package com.myenglish.qacommon.security;

import com.myenglish.qacommon.context.UserContextHolder;
import com.myenglish.qacommon.dto.ApiCode;
import com.myenglish.qacommon.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

import java.util.Arrays;

@Aspect
@Order(1)
public class PermissionAspect {

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) throws Throwable {
        String[] required = requirePermission.value();
        if (required.length == 0) {
            return joinPoint.proceed();
        }

        if (UserContextHolder.hasRole("ROLE_SUPER_ADMIN")) {
            return joinPoint.proceed();
        }

        boolean allowed = requirePermission.requireAll()
                ? Arrays.stream(required).allMatch(UserContextHolder::hasPermission)
                : UserContextHolder.hasAnyPermission(required);

        if (!allowed) {
            throw new BusinessException(ApiCode.FORBIDDEN, "没有操作权限");
        }
        return joinPoint.proceed();
    }
}
