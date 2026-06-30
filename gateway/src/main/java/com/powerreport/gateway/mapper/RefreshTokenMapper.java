package com.powerreport.gateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.powerreport.gateway.entity.RefreshTokenEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Refresh Token Mapper
 */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshTokenEntity> {

    /**
     * 根据 Token 哈希查询有效的 Refresh Token 记录
     */
    @Select("SELECT * FROM refresh_token WHERE token_hash = #{tokenHash} AND revoked = 0 AND expires_at > NOW() LIMIT 1")
    RefreshTokenEntity findValidByHash(@Param("tokenHash") String tokenHash);

    /**
     * 删除指定用户的所有 Refresh Token 记录（用于登出/改密）
     */
    @Select("DELETE FROM refresh_token WHERE username = #{username}")
    void deleteByUsername(@Param("username") String username);

    /**
     * 清理所有已过期的 Refresh Token 记录
     */
    @Select("DELETE FROM refresh_token WHERE expires_at <= NOW()")
    void deleteExpired();
}
