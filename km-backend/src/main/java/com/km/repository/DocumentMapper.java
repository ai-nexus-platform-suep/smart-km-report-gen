package com.km.repository;

import com.km.entity.Document;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文档 Mapper 接口
 */
public interface DocumentMapper {

    int insert(Document document);

    int updateById(Document document);

    int deleteById(@Param("id") String id);

    int deleteByIds(@Param("ids") List<String> ids);

    int deleteByKbId(@Param("kbId") String kbId);

    Document getById(@Param("id") String id);

    List<Document> listByKbId(@Param("kbId") String kbId,
                              @Param("status") String status,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    long countByKbId(@Param("kbId") String kbId,
                     @Param("status") String status);

    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("errorMsg") String errorMsg);
}
