package com.qa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qa.auth.entity.SysPermissionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermissionEntity> {

    List<String> selectPermCodesByUserId(@Param("userId") Long userId);

    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);
}
