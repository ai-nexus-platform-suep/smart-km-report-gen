package com.km.dto.request;

import lombok.Data;

/**
 * 文档分页查询参数
 */
@Data
public class DocumentPageQuery {

    private Integer page = 1;
    private Integer pageSize = 20;
    private String status;
}
