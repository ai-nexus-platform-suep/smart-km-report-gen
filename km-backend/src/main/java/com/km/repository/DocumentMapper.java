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

    /**
     * 仅更新文档标签（独立 Mapper，不与 updateStatus 混用）。
     */
    int updateTags(@Param("id") String id,
                   @Param("tagsJson") String tagsJson);

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

    /**
     * 批量查询文档（用于检索结果回填文档名）
     */
    List<Document> listByIds(@Param("ids") List<String> ids);

    /**
     * 查询指定知识库下状态为 READY 的文档 ID 列表
     * 用于 EPIC-05 BM25 降级搜索
     */
    List<String> listReadyDocIdsByKbIds(@Param("kbIds") List<String> kbIds);
}