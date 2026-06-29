package com.km.repository;

import com.km.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Param;

/**
 * 知识库 Mapper 接口
 */
public interface KnowledgeBaseMapper {

    KnowledgeBase getById(@Param("id") String id);

    int incrementDocCount(@Param("id") String id);

    int decrementDocCount(@Param("id") String id,
                          @Param("count") int count);
}
