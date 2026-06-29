package com.km.dto.request;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量删除请求
 */
@Data
public class BatchDeleteRequest {

    @NotEmpty(message = "删除ID列表不能为空")
    private List<String> ids;
}
