package com.km.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class DocumentStatusUpdateRequest {

    @NotBlank(message = "documentId cannot be blank")
    private String documentId;

    @NotBlank(message = "status cannot be blank")
    private String status;

    private String errorMsg;
    private String jobId;
    private Integer attempt;
    private Integer chunkCount;
}
