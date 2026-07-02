package com.km.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplaceDocumentChunksResponse {
    private String documentId;
    private Integer chunkCount;
}
