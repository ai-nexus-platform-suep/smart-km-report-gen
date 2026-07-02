package com.qa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qa.auth.entity.SysRoleMenuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenuEntity> {

    int deleteByRoleId(@Param("roleId") Long roleId);
}
