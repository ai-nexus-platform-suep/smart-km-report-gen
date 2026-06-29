package com.km.repository;

import com.km.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface KnowledgeBaseMapper {
    int insert(KnowledgeBase kb);
    int update(KnowledgeBase kb);
    int deleteById(@Param("id") String id);
    int batchDeleteByIds(@Param("ids") List<String> ids);
    KnowledgeBase getById(@Param("id") String id);
    List<KnowledgeBase> list(@Param("keyword") String keyword, @Param("docType") String docType,
                             @Param("offset") int offset, @Param("limit") int limit);
    long count(@Param("keyword") String keyword, @Param("docType") String docType);
    int updateDocCount(@Param("id") String id, @Param("delta") int delta);
}
