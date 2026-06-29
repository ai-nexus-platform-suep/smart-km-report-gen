package com.km.repository;

import com.km.entity.Chunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ChunkMapper {
    int batchInsert(@Param("chunks") List<Chunk> chunks);
    int deleteByDocId(@Param("docId") String docId);
    int deleteByKbId(@Param("kbId") String kbId);
    List<Chunk> listByDocId(@Param("docId") String docId, @Param("offset") int offset, @Param("limit") int limit);
    long countByDocId(@Param("docId") String docId);
    long countAll();
}
