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

    /**
     * AI 服务降级时使用的关键词匹配搜索（已废弃，使用 searchByKeywordWithDocIds）
     * @deprecated 使用 {@link #searchByKeywordWithDocIds(String, int, List)} 替代
     */
    @Deprecated
    List<Chunk> searchByKeyword(@Param("keyword") String keyword,
                                @Param("kbIds") List<String> kbIds,
                                @Param("limit") int limit);

    /**
     * BM25 降级搜索：按关键词搜索指定文档列表中的 chunk
     * 对应 EPIC-05 05.5 检索降级策略统一
     */
    List<Chunk> searchByKeywordWithDocIds(@Param("keyword") String keyword,
                                          @Param("limit") int limit,
                                          @Param("docIds") List<String> docIds);
}