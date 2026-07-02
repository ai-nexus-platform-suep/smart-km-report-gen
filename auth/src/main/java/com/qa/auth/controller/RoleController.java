package com.qa.auth.controller;

import com.qa.auth.dto.request.RoleGrantRequest;
import com.qa.auth.dto.request.SaveRoleRequest;
import com.qa.auth.dto.response.RoleVO;
import com.qa.auth.service.RoleService;
import com.myenglish.qacommon.dto.ApiResponse;
import com.myenglish.qacommon.security.OperationLog;
import com.myenglish.qacommon.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @RequirePermission({"auth:role:list", "auth:role:manage"})
    public ApiResponse<List<RoleVO>> list() {
        return ApiResponse.success(roleService.listRoles());
    }

    @GetMapping("/{id}")
    @RequirePermission("auth:role:manage")
    public ApiResponse<RoleVO> detail(@PathVariable Long id) {
        return ApiResponse.success(roleService.getRole(id));
    }

    @PostMapping
    @RequirePermission("auth:role:manage")
    @OperationLog(module = "auth", operation = "新增角色")
    public ApiResponse<RoleVO> create(@Valid @RequestBody SaveRoleRequest req) {
        return ApiResponse.success(roleService.createRole(req));
    }

    @PutMapping("/{id}")
    @RequirePermission("auth:role:manage")
    @OperationLog(module = "auth", operation = "编辑角色")
    public ApiResponse<RoleVO> update(@PathVariable Long id, @Valid @RequestBody SaveRoleRequest req) {
        return ApiResponse.success(roleService.updateRole(id, req));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("auth:role:manage")
    @OperationLog(module = "auth", operation = "删除角色")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/permissions")
    @RequirePermission("auth:role:manage")
    @OperationLog(module = "auth", operation = "角色授权-权限")
    public ApiResponse<Void> grantPermissions(@PathVariable Long id, @RequestBody RoleGrantRequest req) {
        roleService.grantPermissions(id, req);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/menus")
    @RequirePermission("auth:role:manage")
    @OperationLog(module = "auth", operation = "角色授权-菜单")
    public ApiResponse<Void> grantMenus(@PathVariable Long id, @RequestBody RoleGrantRequest req) {
        roleService.grantMenus(id, req);
        return ApiResponse.success(null);
    }
}
