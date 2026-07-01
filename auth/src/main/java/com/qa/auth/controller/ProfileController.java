package com.qa.auth.controller;

import com.qa.auth.dto.request.ChangePasswordRequest;
import com.qa.auth.dto.request.UpdateProfileRequest;
import com.qa.auth.dto.response.UserVO;
import com.qa.auth.service.UserService;
import com.qa.auth.support.CurrentUserResolver;
import com.myenglish.qacommon.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 当前登录用户自助接口：资料查看/编辑、改密、登出。
 * <p>
 * 仅需登录，不要求额外 RBAC 权限码。
 */
@RestController
@RequestMapping("/api/auth/me")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;
    private final CurrentUserResolver currentUserResolver;

    /** 获取当前用户完整资料（含角色，不含密码） */
    @GetMapping("/profile")
    public ApiResponse<UserVO> getProfile() {
        Long userId = currentUserResolver.requireUserId();
        return ApiResponse.success(userService.getProfile(userId));
    }

    /** 编辑当前用户资料（昵称、姓名、邮箱、手机、头像、性别） */
    @PutMapping("/profile")
    public ApiResponse<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        Long userId = currentUserResolver.requireUserId();
        return ApiResponse.success("资料更新成功", userService.updateProfile(userId, req));
    }

    /** 修改当前用户密码（成功后失效全部 Refresh Token） */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        Long userId = currentUserResolver.requireUserId();
        userService.changePassword(userId, req);
        return ApiResponse.success("密码修改成功，请重新登录", null);
    }

    /** 登出：撤销当前用户全部 Refresh Token */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        Long userId = currentUserResolver.requireUserId();
        userService.logout(userId);
        return ApiResponse.success("登出成功", null);
    }
}
