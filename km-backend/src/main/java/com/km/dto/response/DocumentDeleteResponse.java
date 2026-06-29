package com.km.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档删除响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDeleteResponse {

    private String deletedDocumentId;
    private Integer kbDocCount;
}
