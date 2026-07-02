package com.qa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qa.auth.entity.SysRoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRoleEntity> {

    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
