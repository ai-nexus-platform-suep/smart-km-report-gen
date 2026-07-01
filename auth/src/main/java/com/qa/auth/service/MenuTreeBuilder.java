package com.qa.auth.service;

import com.qa.auth.dto.response.MenuVO;
import com.qa.auth.entity.SysMenuEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MenuTreeBuilder {

    public List<MenuVO> buildTree(List<SysMenuEntity> flatList) {
        if (flatList == null || flatList.isEmpty()) {
            return List.of();
        }
        Map<Long, MenuVO> index = flatList.stream()
                .map(this::toVo)
                .collect(Collectors.toMap(MenuVO::getId, v -> v, (a, b) -> a));

        List<MenuVO> roots = new ArrayList<>();
        for (MenuVO node : index.values()) {
            Long parentId = node.getParentId();
            if (parentId == null || parentId == 0L || !index.containsKey(parentId)) {
                roots.add(node);
            } else {
                index.get(parentId).getChildren().add(node);
            }
        }
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<MenuVO> nodes) {
        nodes.sort(Comparator.comparing(MenuVO::getSortOrder, Comparator.nullsLast(Integer::compareTo)));
        for (MenuVO node : nodes) {
            if (!node.getChildren().isEmpty()) {
                sortTree(node.getChildren());
            }
        }
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
        vo.setHidden(entity.getHidden());
        vo.setKeepAlive(entity.getKeepAlive());
        vo.setAlwaysShow(entity.getAlwaysShow());
        return vo;
    }
}
