package com.km.common.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PageResult 单元测试。
 * 覆盖：全参构造、无参构造、Lombok getter/setter、total 与 list 独立性、泛型支持、空列表、大 total。
 */
class PageResultTest {

    @Test
    void shouldConstructWithAllArgsConstructor() {
        List<String> items = Arrays.asList("a", "b", "c");
        PageResult<String> result = new PageResult<>(items, 100L, 1, 10);

        assertEquals(items, result.getList());
        assertEquals(100L, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(10, result.getPageSize());
    }

    @Test
    void shouldConstructWithNoArgsConstructor() {
        PageResult<Object> result = new PageResult<>();

        assertNotNull(result);
        assertNull(result.getList());
        assertEquals(0L, result.getTotal());
        assertEquals(0, result.getPage());
        assertEquals(0, result.getPageSize());
    }

    @Test
    void shouldSetAndGetFields() {
        PageResult<String> result = new PageResult<>();
        List<String> items = Arrays.asList("x", "y");

        result.setList(items);
        result.setTotal(50L);
        result.setPage(2);
        result.setPageSize(20);

        assertEquals(items, result.getList());
        assertEquals(50L, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(20, result.getPageSize());
    }

    @Test
    void shouldCalculateTotal() {
        List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);
        int listSize = items.size();
        long explicitTotal = 200L;

        PageResult<Integer> result = new PageResult<>();
        result.setList(items);
        result.setTotal(explicitTotal);

        assertEquals(5, result.getList().size());
        assertEquals(200L, result.getTotal());
        assertNotEquals(listSize, result.getTotal());
    }

    @Test
    void shouldSupportGenericType() {
        PageResult<String> stringResult = new PageResult<>();
        stringResult.setList(Arrays.asList("hello", "world"));
        assertEquals("hello", stringResult.getList().get(0));

        PageResult<Long> longResult = new PageResult<>();
        longResult.setList(Arrays.asList(1L, 2L, 3L));
        assertEquals(Long.valueOf(2L), longResult.getList().get(1));
    }

    @Test
    void shouldHandleEmptyList() {
        PageResult<String> result = new PageResult<>(new ArrayList<>(), 0L, 1, 10);

        assertNotNull(result.getList());
        assertTrue(result.getList().isEmpty());
        assertEquals(0L, result.getTotal());
    }

    @Test
    void shouldHandleLargeTotal() {
        List<String> items = Arrays.asList("a", "b");
        PageResult<String> result = new PageResult<>(items, 9999L, 1, 10);

        assertEquals(2, result.getList().size());
        assertEquals(9999L, result.getTotal());
        assertTrue(result.getTotal() > result.getList().size());
    }
}
