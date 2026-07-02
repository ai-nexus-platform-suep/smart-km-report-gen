package com.qa.auth.dto.response;

import cn.hutool.core.bean.BeanUtil;
import lombok.Data;

import java.util.List;

/**
 * 当前登录用户信息（/me 接口返回），含权限列表
 */
@Data
public class CurrentUserVO {

    private Long id;
    private String username;
    private String nickname;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private Integer gender;
    private List<String> roles;
    private List<String> permissions;

    /** 从 UserVO + 权限列表构建 */
    public static CurrentUserVO from(UserVO profile, List<String> permissions) {
        CurrentUserVO vo = BeanUtil.copyProperties(profile, CurrentUserVO.class);
        vo.setPermissions(permissions);
        return vo;
    }
}
