package com.qa.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qa.auth.dto.request.RoleGrantRequest;
import com.qa.auth.dto.request.SaveRoleRequest;
import com.qa.auth.dto.response.RoleVO;
import com.qa.auth.entity.SysRoleEntity;
import com.qa.auth.entity.SysRoleMenuEntity;
import com.qa.auth.entity.SysRolePermissionEntity;
import com.qa.auth.mapper.SysMenuMapper;
import com.qa.auth.mapper.SysPermissionMapper;
import com.qa.auth.mapper.SysRoleMapper;
import com.qa.auth.mapper.SysRoleMenuMapper;
import com.qa.auth.mapper.SysRolePermissionMapper;
import com.myenglish.qacommon.dto.ApiCode;
import com.myenglish.qacommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysMenuMapper sysMenuMapper;

    public List<RoleVO> listRoles() {
        return sysRoleMapper.selectList(
                new LambdaQueryWrapper<SysRoleEntity>().orderByAsc(SysRoleEntity::getSortOrder))
                .stream().map(this::toVo).collect(Collectors.toList());
    }

    public RoleVO getRole(Long id) {
        SysRoleEntity role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "角色不存在");
        }
        RoleVO vo = toVo(role);
        vo.setPermissionIds(sysPermissionMapper.selectPermissionIdsByRoleId(id));
        vo.setMenuIds(sysMenuMapper.selectMenuIdsByRoleId(id));
        return vo;
    }

    @Transactional
    public RoleVO createRole(SaveRoleRequest req) {
        if (existsRoleCode(req.getRoleCode(), null)) {
            throw new BusinessException(ApiCode.DATA_ALREADY_EXISTS, "角色编码已存在");
        }
        SysRoleEntity role = new SysRoleEntity();
        apply(role, req);
        role.setEnabled(req.getEnabled() != null ? req.getEnabled() : true);
        sysRoleMapper.insert(role);
        return toVo(role);
    }

    @Transactional
    public RoleVO updateRole(Long id, SaveRoleRequest req) {
        SysRoleEntity role = sysRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "角色不存在");
        }
        if (existsRoleCode(req.getRoleCode(), id)) {
            throw new BusinessException(ApiCode.DATA_ALREADY_EXISTS, "角色编码已存在");
        }
        apply(role, req);
        sysRoleMapper.updateById(role);
        return toVo(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        if (sysRoleMapper.selectById(id) == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "角色不存在");
        }
        sysRolePermissionMapper.deleteByRoleId(id);
        sysRoleMenuMapper.deleteByRoleId(id);
        sysRoleMapper.deleteById(id);
    }

    @Transactional
    public void grantPermissions(Long roleId, RoleGrantRequest req) {
        ensureRole(roleId);
        sysRolePermissionMapper.deleteByRoleId(roleId);
        if (req.getPermissionIds() != null) {
            for (Long permissionId : req.getPermissionIds()) {
                SysRolePermissionEntity rp = new SysRolePermissionEntity();
                rp.setRoleId(roleId);
                rp.setPermissionId(permissionId);
                sysRolePermissionMapper.insert(rp);
            }
        }
    }

    @Transactional
    public void grantMenus(Long roleId, RoleGrantRequest req) {
        ensureRole(roleId);
        sysRoleMenuMapper.deleteByRoleId(roleId);
        if (req.getMenuIds() != null) {
            for (Long menuId : req.getMenuIds()) {
                SysRoleMenuEntity rm = new SysRoleMenuEntity();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                sysRoleMenuMapper.insert(rm);
            }
        }
    }

    private void ensureRole(Long roleId) {
        if (sysRoleMapper.selectById(roleId) == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "角色不存在");
        }
    }

    private boolean existsRoleCode(String roleCode, Long excludeId) {
        LambdaQueryWrapper<SysRoleEntity> wrapper = new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getRoleCode, roleCode);
        if (excludeId != null) {
            wrapper.ne(SysRoleEntity::getId, excludeId);
        }
        return sysRoleMapper.selectCount(wrapper) > 0;
    }

    private void apply(SysRoleEntity role, SaveRoleRequest req) {
        role.setRoleCode(req.getRoleCode());
        role.setRoleName(req.getRoleName());
        role.setDescription(req.getDescription());
        if (req.getEnabled() != null) role.setEnabled(req.getEnabled());
        if (req.getSortOrder() != null) role.setSortOrder(req.getSortOrder());
    }

    private RoleVO toVo(SysRoleEntity role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDescription(role.getDescription());
        vo.setEnabled(role.getEnabled());
        vo.setSortOrder(role.getSortOrder());
        vo.setCreatedAt(role.getCreatedAt());
        return vo;
    }
}
