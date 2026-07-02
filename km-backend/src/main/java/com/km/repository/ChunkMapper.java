package com.km.repository;

import com.km.entity.Chunk;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 切片 Mapper 接口
 */
public interface ChunkMapper {

    int insert(Chunk chunk);

    int insertBatch(List<Chunk> chunks);

    int deleteByDocId(@Param("docId") String docId);

    int deleteByDocIds(@Param("docIds") List<String> docIds);

    List<Chunk> listByDocId(@Param("docId") String docId,
                            @Param("offset") int offset,
                            @Param("limit") int limit);

    long countByDocId(@Param("docId") String docId);

    /** AI 服务降级时使用的关键词模糊匹配 */
    List<Chunk> searchByKeyword(@Param("keyword") String keyword,
                                @Param("kbIds") List<String> kbIds,
                                @Param("limit") int limit);
}
