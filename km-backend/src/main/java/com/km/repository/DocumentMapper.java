package com.km.repository;

import com.km.entity.Document;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 鏂囨。 Mapper 鎺ュ彛
 */
public interface DocumentMapper {

    int insert(Document document);

    int updateById(Document document);

    int deleteById(@Param("id") String id);

    /**
     * 浠呮洿鏂版枃妗ｆ爣绛撅紙鐙珛 Mapper锛屼笉涓?updateStatus 娣风敤锛夈€?
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
     * 鎵归噺鏌ヨ鏂囨。锛堢敤浜庢绱㈢粨鏋滃洖濉枃妗ｅ悕锛?
     */
    List<Document> listByIds(@Param("ids") List<String> ids);

    /**
     * 鏌ヨ鎸囧畾鐭ヨ瘑搴撲笅鐘舵€佷负 READY 鐨勬枃妗?ID 鍒楄〃
     * 鐢ㄤ簬 EPIC-05 BM25 闄嶇骇鎼滅储
     */
    List<String> listReadyDocIdsByKbIds(@Param("kbIds") List<String> kbIds);

    /**
     * 查询全部 READY 状态文档 ID（未指定知识库时全库搜索）
     * 用于 EPIC-05 BM25 降级搜索
     */
    List<String> listAllReadyDocIds();
}