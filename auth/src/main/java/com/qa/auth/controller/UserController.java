package com.qa.auth.controller;

import com.qa.auth.dto.request.AssignRolesRequest;
import com.qa.auth.dto.request.CreateUserRequest;
import com.qa.auth.dto.request.UpdateUserRequest;
import com.qa.auth.dto.response.UserVO;
import com.qa.auth.service.UserService;
import com.myenglish.qacommon.dto.ApiResponse;
import com.myenglish.qacommon.security.OperationLog;
import com.myenglish.qacommon.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @RequirePermission("auth:user:list")
    public ApiResponse<List<UserVO>> list() {
        return ApiResponse.success(userService.listUsers());
    }

    @PostMapping
    @RequirePermission("auth:user:create")
    @OperationLog(module = "auth", operation = "新增用户")
    public ApiResponse<UserVO> create(@Valid @RequestBody CreateUserRequest req) {
        return ApiResponse.success(userService.createUser(req));
    }

    @GetMapping("/{id}")
    @RequirePermission("auth:user:list")
    public ApiResponse<UserVO> get(@PathVariable Long id) {
        return ApiResponse.success(userService.getProfile(id));
    }

    @PutMapping("/{id}")
    @RequirePermission("auth:user:update")
    @OperationLog(module = "auth", operation = "编辑用户")
    public ApiResponse<UserVO> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req) {
        return ApiResponse.success(userService.updateUser(id, req));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("auth:user:delete")
    @OperationLog(module = "auth", operation = "删除用户")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/roles")
    @RequirePermission("auth:role:assign")
    @OperationLog(module = "auth", operation = "分配角色")
    public ApiResponse<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AssignRolesRequest req) {
        userService.assignRoles(id, req.getRoleCodes());
        return ApiResponse.success(null);
    }
}
