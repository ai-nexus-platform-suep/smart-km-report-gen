package com.myenglish.qacommon.dto;

import java.util.List;

import lombok.Data;

/**
 * 统一分页响应
 */
@Data
public class PageResult<T> {
    /**
     * 当前页数据
     */
    private List<T> items;

    /**
     * 总条数
     */
    private long total;

    /**
     * 当前页码（从 1 开始）
     */
    private int page;

    /**
     * 每页条数
     */
    private int size;
}
