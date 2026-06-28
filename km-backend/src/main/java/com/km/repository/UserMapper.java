package com.km.repository;

import com.km.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    User findByUsername(@Param("username") String username);

    User findById(@Param("id") Long id);

    int insert(User user);

    int updateById(User user);

    int countByUsername(@Param("username") String username);
    int clearToken(@Param("id") Long id);
}
