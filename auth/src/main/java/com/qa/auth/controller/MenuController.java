package com.qa.auth.controller;

import com.qa.auth.dto.request.SaveMenuRequest;
import com.qa.auth.dto.response.MenuVO;
import com.qa.auth.service.MenuService;
import com.myenglish.qacommon.dto.ApiResponse;
import com.myenglish.qacommon.security.OperationLog;
import com.myenglish.qacommon.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    @RequirePermission({"auth:menu:list", "auth:menu:manage"})
    public ApiResponse<List<MenuVO>> tree() {
        return ApiResponse.success(menuService.listMenuTree());
    }

    @PostMapping
    @RequirePermission("auth:menu:manage")
    @OperationLog(module = "auth", operation = "新增菜单")
    public ApiResponse<MenuVO> create(@Valid @RequestBody SaveMenuRequest req) {
        return ApiResponse.success(menuService.createMenu(req));
    }

    @PutMapping("/{id}")
    @RequirePermission("auth:menu:manage")
    @OperationLog(module = "auth", operation = "编辑菜单")
    public ApiResponse<MenuVO> update(@PathVariable Long id, @Valid @RequestBody SaveMenuRequest req) {
        return ApiResponse.success(menuService.updateMenu(id, req));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("auth:menu:manage")
    @OperationLog(module = "auth", operation = "删除菜单")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return ApiResponse.success(null);
    }
}
