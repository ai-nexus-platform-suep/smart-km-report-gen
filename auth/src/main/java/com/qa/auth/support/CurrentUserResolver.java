package com.qa.auth.support;

import com.myenglish.qacommon.context.UserContextHolder;
import com.myenglish.qacommon.dto.ApiCode;
import com.myenglish.qacommon.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 解析当前登录用户（Gateway 注入 user-id 或 JwtContextFilter 从 JWT 恢复）。
 */
@Component
public class CurrentUserResolver {

    public Long requireUserId() {
        Long userId = UserContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(ApiCode.UNAUTHORIZED, "未认证，请先登录");
        }
        return userId;
    }
}
