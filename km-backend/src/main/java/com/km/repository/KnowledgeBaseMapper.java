package com.km.repository;

import com.km.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KnowledgeBaseMapper {

    KnowledgeBase getById(@Param("id") String id);

    int incrementDocCount(@Param("id") String id);

    int decrementDocCount(@Param("id") String id,
                          @Param("count") int count);

    List<KnowledgeBase> findAll(@Param("docType") String docType,
                                @Param("keyword") String keyword,
                                @Param("offset") int offset,
                                @Param("limit") int limit);

    int countAll(@Param("docType") String docType,
                 @Param("keyword") String keyword);

    int insert(KnowledgeBase kb);

    int updateById(KnowledgeBase kb);

    int deleteById(@Param("id") String id);

    int batchDelete(@Param("ids") List<String> ids);
}
