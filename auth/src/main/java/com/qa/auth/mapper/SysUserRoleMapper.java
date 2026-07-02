package com.qa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qa.auth.entity.SysUserRoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRoleEntity> {

    int deleteByUserId(@Param("userId") Long userId);

    /** 查询拥有指定角色的所有 userId */
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);
}
