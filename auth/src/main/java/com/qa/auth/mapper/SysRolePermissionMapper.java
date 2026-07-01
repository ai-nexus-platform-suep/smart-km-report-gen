package com.qa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qa.auth.entity.SysRolePermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermissionEntity> {

    int deleteByRoleId(@Param("roleId") Long roleId);
}
