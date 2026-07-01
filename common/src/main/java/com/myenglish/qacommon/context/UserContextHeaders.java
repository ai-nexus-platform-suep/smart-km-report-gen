package com.myenglish.qacommon.context;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Gateway 注入的用户上下文请求头（与 Python qa-agent 统一）。
 */
public final class UserContextHeaders {

    public static final String USER_ID = "user-id";
    public static final String USERNAME = "username";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "permissions";

    private UserContextHeaders() {
    }
}
