package com.qa.auth.config;

import com.qa.auth.entity.SysLogEntity;
import com.qa.auth.service.SysLogService;
import com.myenglish.qacommon.context.UserContextHolder;
import com.myenglish.qacommon.security.OperationLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final SysLogService sysLogService;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        SysLogEntity logEntity = new SysLogEntity();
        logEntity.setUserId(UserContextHolder.getUserId());
        logEntity.setUsername(UserContextHolder.getUsername());
        logEntity.setModule(operationLog.module());
        logEntity.setOperation(operationLog.operation());
        logEntity.setMethod(joinPoint.getSignature().toShortString());

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            logEntity.setRequestUri(request.getRequestURI());
            logEntity.setRequestMethod(request.getMethod());
            logEntity.setRequestIp(resolveIp(request));
            logEntity.setUserAgent(request.getHeader("User-Agent"));
        }

        try {
            Object result = joinPoint.proceed();
            logEntity.setStatus(true);
            logEntity.setResponseCode(200);
            return result;
        } catch (Throwable ex) {
            logEntity.setStatus(false);
            logEntity.setErrorMsg(ex.getMessage());
            logEntity.setResponseCode(500);
            throw ex;
        } finally {
            logEntity.setCostMs((int) (System.currentTimeMillis() - start));
            sysLogService.save(logEntity);
        }
    }

    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
