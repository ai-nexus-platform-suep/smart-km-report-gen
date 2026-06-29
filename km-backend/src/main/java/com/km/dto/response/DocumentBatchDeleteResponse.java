package com.km.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文档批量删除响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentBatchDeleteResponse {

    private List<String> deletedIds;
    private Integer kbDocCount;
}
