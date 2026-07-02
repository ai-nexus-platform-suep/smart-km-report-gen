package com.myenglish.qachat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myenglish.qachat.entity.ModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfig> {

    /**
     * 查询用户在指定场景下的默认启用配置
     */
    @Select("SELECT * FROM model_config WHERE user_id = #{userId} AND scenario = #{scenario} AND enabled = 1 AND is_default = 1 LIMIT 1")
    ModelConfig selectDefault(@Param("userId") Long userId, @Param("scenario") String scenario);
}
