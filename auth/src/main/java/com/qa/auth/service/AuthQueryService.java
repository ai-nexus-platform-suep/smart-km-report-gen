package com.qa.auth.service;

import com.qa.auth.dto.UserAuthorities;
import com.qa.auth.entity.SysMenuEntity;
import com.qa.auth.mapper.SysMenuMapper;
import com.qa.auth.mapper.SysPermissionMapper;
import com.qa.auth.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthQueryService {

    private final SysRoleMapper sysRoleMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysMenuMapper sysMenuMapper;

    public UserAuthorities loadAuthorities(Long userId) {
        if (userId == null) {
            return UserAuthorities.builder()
                    .roles(List.of())
                    .permissions(List.of())
                    .build();
        }
        List<String> roles = sysRoleMapper.selectRoleCodesByUserId(userId);
        List<String> permissions = sysPermissionMapper.selectPermCodesByUserId(userId);
        return UserAuthorities.builder()
                .roles(roles != null ? roles : Collections.emptyList())
                .permissions(permissions != null ? permissions : Collections.emptyList())
                .build();
    }

    public List<SysMenuEntity> loadMenus(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<SysMenuEntity> menus = sysMenuMapper.selectMenusByUserId(userId);
        return menus != null ? menus : Collections.emptyList();
    }
}
