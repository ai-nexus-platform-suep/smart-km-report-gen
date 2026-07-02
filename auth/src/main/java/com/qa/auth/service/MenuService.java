package com.qa.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qa.auth.dto.request.SaveMenuRequest;
import com.qa.auth.dto.response.MenuVO;
import com.qa.auth.entity.SysMenuEntity;
import com.qa.auth.mapper.SysMenuMapper;
import com.myenglish.qacommon.dto.ApiCode;
import com.myenglish.qacommon.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuService {

    private final SysMenuMapper sysMenuMapper;
    private final MenuTreeBuilder menuTreeBuilder;

    public List<MenuVO> listMenuTree() {
        List<SysMenuEntity> all = sysMenuMapper.selectList(
                new LambdaQueryWrapper<SysMenuEntity>()
                        .eq(SysMenuEntity::getEnabled, true)
                        .orderByAsc(SysMenuEntity::getLevel, SysMenuEntity::getSortOrder));
        return menuTreeBuilder.buildTree(all);
    }

    public List<MenuVO> listUserMenuTree(Long userId) {
        return menuTreeBuilder.buildTree(
                sysMenuMapper.selectMenusByUserId(userId).stream()
                        .filter(m -> !"BUTTON".equals(m.getMenuType()))
                        .toList());
    }

    @Transactional
    public MenuVO createMenu(SaveMenuRequest req) {
        if (existsMenuCode(req.getMenuCode(), null)) {
            throw new BusinessException(ApiCode.DATA_ALREADY_EXISTS, "菜单编码已存在");
        }
        SysMenuEntity menu = new SysMenuEntity();
        apply(menu, req);
        fillTreeFields(menu);
        menu.setEnabled(req.getEnabled() != null ? req.getEnabled() : true);
        sysMenuMapper.insert(menu);
        return toVo(menu);
    }

    @Transactional
    public MenuVO updateMenu(Long id, SaveMenuRequest req) {
        SysMenuEntity menu = sysMenuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(ApiCode.NOT_FOUND, "菜单不存在");
        }
        if (existsMenuCode(req.getMenuCode(), id)) {
            throw new BusinessException(ApiCode.DATA_ALREADY_EXISTS, "菜单编码已存在");
        }
        apply(menu, req);
        fillTreeFields(menu);
        sysMenuMapper.updateById(menu);
        return toVo(menu);
    }

    @Transactional
    public void deleteMenu(Long id) {
        long childCount = sysMenuMapper.selectCount(
                new LambdaQueryWrapper<SysMenuEntity>().eq(SysMenuEntity::getParentId, id));
        if (childCount > 0) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "存在子菜单，无法删除");
        }
        sysMenuMapper.deleteById(id);
    }

    private void fillTreeFields(SysMenuEntity menu) {
        Long parentId = menu.getParentId() != null ? menu.getParentId() : 0L;
        menu.setParentId(parentId);
        if (parentId == 0L) {
            menu.setAncestors("0");
            menu.setLevel(1);
            return;
        }
        SysMenuEntity parent = sysMenuMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(ApiCode.BAD_REQUEST, "父菜单不存在");
        }
        menu.setAncestors(parent.getAncestors() + "," + parent.getId());
        menu.setLevel(parent.getLevel() + 1);
    }

    private boolean existsMenuCode(String menuCode, Long excludeId) {
        LambdaQueryWrapper<SysMenuEntity> wrapper = new LambdaQueryWrapper<SysMenuEntity>()
                .eq(SysMenuEntity::getMenuCode, menuCode);
        if (excludeId != null) {
            wrapper.ne(SysMenuEntity::getId, excludeId);
        }
        return sysMenuMapper.selectCount(wrapper) > 0;
    }

    private void apply(SysMenuEntity menu, SaveMenuRequest req) {
        if (req.getParentId() != null) menu.setParentId(req.getParentId());
        menu.setMenuName(req.getMenuName());
        menu.setMenuCode(req.getMenuCode());
        menu.setRoutePath(req.getRoutePath());
        menu.setRouteName(req.getRouteName());
        menu.setComponent(req.getComponent());
        menu.setRedirect(req.getRedirect());
        menu.setPermCode(req.getPermCode());
        menu.setMenuType(req.getMenuType() != null ? req.getMenuType() : "MENU");
        menu.setIcon(req.getIcon());
        if (req.getSortOrder() != null) menu.setSortOrder(req.getSortOrder());
        if (req.getVisible() != null) menu.setVisible(req.getVisible());
        if (req.getHidden() != null) menu.setHidden(req.getHidden());
        if (req.getKeepAlive() != null) menu.setKeepAlive(req.getKeepAlive());
        if (req.getAlwaysShow() != null) menu.setAlwaysShow(req.getAlwaysShow());
        if (req.getEnabled() != null) menu.setEnabled(req.getEnabled());
    }

    private MenuVO toVo(SysMenuEntity entity) {
        MenuVO vo = new MenuVO();
        vo.setId(entity.getId());
        vo.setParentId(entity.getParentId());
        vo.setMenuName(entity.getMenuName());
        vo.setMenuCode(entity.getMenuCode());
        vo.setRoutePath(entity.getRoutePath());
        vo.setRouteName(entity.getRouteName());
        vo.setComponent(entity.getComponent());
        vo.setRedirect(entity.getRedirect());
        vo.setPermCode(entity.getPermCode());
        vo.setMenuType(entity.getMenuType());
        vo.setIcon(entity.getIcon());
        vo.setSortOrder(entity.getSortOrder());
        return vo;
    }
}
