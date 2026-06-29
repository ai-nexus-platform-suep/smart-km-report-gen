package com.km.dto.response;

import com.km.vo.DocumentVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档上传响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadResponse {

    private DocumentVO document;
    private Integer kbDocCount;
}
