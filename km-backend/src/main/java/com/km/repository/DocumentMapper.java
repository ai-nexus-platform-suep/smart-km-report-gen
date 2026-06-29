package com.km.repository;

import com.km.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface DocumentMapper {
    int insert(Document doc);
    int updateStatus(@Param("id") String id, @Param("status") String status, @Param("errorMsg") String errorMsg);
    int deleteById(@Param("id") String id);
    int batchDeleteByIds(@Param("kbId") String kbId, @Param("ids") List<String> ids);
    Document getById(@Param("id") String id);
    List<Document> listByKbId(@Param("kbId") String kbId, @Param("status") String status,
                               @Param("offset") int offset, @Param("limit") int limit);
    long countByKbId(@Param("kbId") String kbId, @Param("status") String status);
    long countAll();
    long countByStatus(@Param("status") String status);
}
