package com.km.service.impl;

import com.km.common.dto.PageResult;
import com.km.common.exception.BusinessException;
import com.km.common.exception.ErrorCode;
import com.km.dto.request.CreateKnowledgeBaseRequest;
import com.km.dto.request.UpdateKnowledgeBaseRequest;
import com.km.dto.response.KnowledgeBaseVO;
import com.km.entity.KnowledgeBase;
import com.km.repository.KnowledgeBaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * KnowledgeBaseServiceImpl 单元测试。
 * 覆盖：列表查询、创建、查询、更新、删除、批量删除、空结果、关键词过滤。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    private KnowledgeBaseServiceImpl knowledgeBaseService;

    private static final Long TEST_OWNER_ID = 1L;

    @BeforeEach
    void setUp() {
        knowledgeBaseService = new KnowledgeBaseServiceImpl(knowledgeBaseMapper);
    }

    // ====== 列表查询 ======

    @Test
    void shouldListKnowledgeBases() {
        KnowledgeBase kb1 = createKnowledgeBase("kb-1", "技术文档", "tech", "通用文档");
        KnowledgeBase kb2 = createKnowledgeBase("kb-2", "运维手册", "ops", "通用文档");
        when(knowledgeBaseMapper.findAll(any(), any(), anyInt(), anyInt()))
                .thenReturn(Arrays.asList(kb1, kb2));
        when(knowledgeBaseMapper.countAll(any(), any())).thenReturn(2);

        PageResult<KnowledgeBaseVO> result = knowledgeBaseService.list(null, null, 1, 10);

        assertNotNull(result);
        assertEquals(2, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(10, result.getPageSize());
        assertEquals(2, result.getList().size());
        assertEquals("kb-1", result.getList().get(0).getId());
        assertEquals("技术文档", result.getList().get(0).getName());
        assertEquals("kb-2", result.getList().get(1).getId());
    }

    @Test
    void shouldListWithKeywordFilter() {
        KnowledgeBase kb = createKnowledgeBase("kb-1", "变压器资料", "power", "电力文档");
        when(knowledgeBaseMapper.findAll(eq("电力文档"), eq("变压器"), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(kb));
        when(knowledgeBaseMapper.countAll(eq("电力文档"), eq("变压器"))).thenReturn(1);

        PageResult<KnowledgeBaseVO> result = knowledgeBaseService.list("电力文档", "变压器", 1, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals("变压器资料", result.getList().get(0).getName());
        verify(knowledgeBaseMapper).findAll("电力文档", "变压器", 0, 20);
    }

    @Test
    void shouldListWithEmptyResult() {
        when(knowledgeBaseMapper.findAll(any(), any(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(knowledgeBaseMapper.countAll(any(), any())).thenReturn(0);

        PageResult<KnowledgeBaseVO> result = knowledgeBaseService.list(null, null, 1, 10);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
        assertEquals(0, result.getList().size());
    }

    // ====== 创建 ======

    @Test
    void shouldCreateKnowledgeBase() {
        CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
        request.setName("我的知识库");
        request.setDescription("测试描述");
        request.setDocType("技术报告论文");
        request.setChunkStrategy(java.util.Collections.singletonMap("type", "heading"));
        request.setSearchStrategy("vector");

        // create 内部先 insert 再 getById，mock getById 返回插入的数据
        when(knowledgeBaseMapper.getById(anyString())).thenAnswer(inv -> {
            KnowledgeBase kb = new KnowledgeBase();
            kb.setId(inv.getArgument(0));
            kb.setName("我的知识库");
            kb.setDescription("测试描述");
            kb.setDocType("技术报告论文");
            kb.setSearchStrategy("vector");
            kb.setOwnerId(TEST_OWNER_ID);
            kb.setDocCount(0);
            return kb;
        });

        KnowledgeBaseVO vo = knowledgeBaseService.create(request, TEST_OWNER_ID);

        assertNotNull(vo);
        assertNotNull(vo.getId());
        assertFalse(vo.getId().contains("-"), "ID should not contain dashes");
        assertEquals(32, vo.getId().length(), "UUID without dashes should be 32 chars");
        assertEquals("我的知识库", vo.getName());
        assertEquals("测试描述", vo.getDescription());
        assertEquals("技术报告论文", vo.getDocType());
        assertEquals("vector", vo.getSearchStrategy());
        assertEquals(TEST_OWNER_ID, vo.getOwnerId());

        ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseMapper).insert(captor.capture());
        KnowledgeBase saved = captor.getValue();
        assertEquals("我的知识库", saved.getName());
        assertNotNull(saved.getId());
    }

    @Test
    void shouldCreateKnowledgeBaseWithHeadingStrategy() {
        CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
        request.setName("知识库");
        request.setDocType("通用文档");
        request.setChunkStrategy(java.util.Collections.singletonMap("type", "heading"));
        request.setSearchStrategy("vector_rerank");

        when(knowledgeBaseMapper.getById(anyString())).thenAnswer(inv -> {
            KnowledgeBase kb = new KnowledgeBase();
            kb.setId(inv.getArgument(0));
            kb.setName("知识库");
            kb.setDocType("通用文档");
            kb.setSearchStrategy("vector_rerank");
            kb.setOwnerId(TEST_OWNER_ID);
            kb.setDocCount(0);
            return kb;
        });

        KnowledgeBaseVO vo = knowledgeBaseService.create(request, TEST_OWNER_ID);

        assertNotNull(vo);
        assertEquals("通用文档", vo.getDocType());
        assertEquals("vector_rerank", vo.getSearchStrategy());
    }

    // ====== 查询 ======

    @Test
    void shouldGetKnowledgeBaseById() {
        KnowledgeBase kb = createKnowledgeBase("kb-1", "知识库A", "desc", "通用文档");
        when(knowledgeBaseMapper.getById("kb-1")).thenReturn(kb);

        KnowledgeBaseVO vo = knowledgeBaseService.getById("kb-1");

        assertNotNull(vo);
        assertEquals("kb-1", vo.getId());
        assertEquals("知识库A", vo.getName());
        assertEquals("desc", vo.getDescription());
        assertEquals("通用文档", vo.getDocType());
    }

    @Test
    void shouldThrowExceptionWhenGetNonExistentKb() {
        when(knowledgeBaseMapper.getById("nonexistent")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.getById("nonexistent"));
        assertEquals(ErrorCode.KM_KB_001.getCode(), ex.getCode());
    }

    // ====== 更新 ======

    @Test
    void shouldUpdateKnowledgeBase() {
        KnowledgeBase existing = createKnowledgeBase("kb-1", "旧名称", "旧描述", "通用文档");
        when(knowledgeBaseMapper.getById("kb-1")).thenReturn(existing);

        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest();
        request.setName("新名称");
        request.setDescription("新描述");

        KnowledgeBaseVO vo = knowledgeBaseService.update("kb-1", request);

        ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseMapper).updateById(captor.capture());
        KnowledgeBase updated = captor.getValue();
        assertEquals("kb-1", updated.getId());
        assertEquals("新名称", updated.getName());
        assertEquals("新描述", updated.getDescription());
    }

    @Test
    void shouldThrowExceptionWhenUpdateNonExistentKb() {
        when(knowledgeBaseMapper.getById("nonexistent")).thenReturn(null);

        UpdateKnowledgeBaseRequest request = new UpdateKnowledgeBaseRequest();
        request.setName("新名称");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.update("nonexistent", request));
        assertEquals(ErrorCode.KM_KB_001.getCode(), ex.getCode());
        verify(knowledgeBaseMapper, never()).updateById(any());
    }

    // ====== 删除 ======

    @Test
    void shouldDeleteKnowledgeBase() {
        KnowledgeBase existing = createKnowledgeBase("kb-1", "待删除", "", "通用文档");
        when(knowledgeBaseMapper.getById("kb-1")).thenReturn(existing);

        assertDoesNotThrow(() -> knowledgeBaseService.deleteById("kb-1"));
        verify(knowledgeBaseMapper).deleteById("kb-1");
    }

    @Test
    void shouldThrowExceptionWhenDeleteNonExistentKb() {
        when(knowledgeBaseMapper.getById("nonexistent")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.deleteById("nonexistent"));
        assertEquals(ErrorCode.KM_KB_001.getCode(), ex.getCode());
        verify(knowledgeBaseMapper, never()).deleteById(any());
    }

    // ====== 批量删除 ======

    @Test
    void shouldBatchDeleteKnowledgeBases() {
        List<String> ids = Arrays.asList("kb-1", "kb-2", "kb-3");
        KnowledgeBase kb1 = createKnowledgeBase("kb-1", "KB1", "", "通用文档");
        KnowledgeBase kb2 = createKnowledgeBase("kb-2", "KB2", "", "通用文档");
        KnowledgeBase kb3 = createKnowledgeBase("kb-3", "KB3", "", "通用文档");
        when(knowledgeBaseMapper.getById("kb-1")).thenReturn(kb1);
        when(knowledgeBaseMapper.getById("kb-2")).thenReturn(kb2);
        when(knowledgeBaseMapper.getById("kb-3")).thenReturn(kb3);

        assertDoesNotThrow(() -> knowledgeBaseService.batchDelete(ids));
        verify(knowledgeBaseMapper).batchDelete(ids);
    }

    @Test
    void shouldThrowExceptionWhenBatchDeleteEmptyList() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> knowledgeBaseService.batchDelete(Collections.emptyList()));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
    }

    // ====== 辅助方法 ======

    private KnowledgeBase createKnowledgeBase(String id, String name, String description, String docType) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setName(name);
        kb.setDescription(description);
        kb.setDocType(docType);
        kb.setChunkStrategyJson("{\"chunkSize\":512}");
        kb.setSearchStrategy("vector_rerank");
        kb.setDocCount(5);
        kb.setOwnerId(TEST_OWNER_ID);
        kb.setCreatedAt(LocalDateTime.now().minusDays(1));
        kb.setUpdatedAt(LocalDateTime.now());
        return kb;
    }
}
