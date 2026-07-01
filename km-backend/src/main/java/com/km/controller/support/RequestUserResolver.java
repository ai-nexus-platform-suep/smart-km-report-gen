package com.km.controller.support;

import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class RequestUserResolver {

    private static final String USER_ID_HEADER = "userid";

    public Long requireUserId(String headerValue) {
        if (headerValue == null || headerValue.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Missing required header: " + USER_ID_HEADER);
        }
        try {
            long userId = Long.parseLong(headerValue.trim());
            if (userId <= 0) {
                throw new NumberFormatException("non-positive user id");
            }
            return userId;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, USER_ID_HEADER + " must be a positive integer");
        }
    }
}
