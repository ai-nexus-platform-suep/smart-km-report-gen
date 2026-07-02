package com.km.dto.request;

import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class ReplaceDocumentChunksRequest {

    private String jobId;
    private String kbId;

    @NotEmpty(message = "chunks cannot be empty")
    @Valid
    private List<ChunkItem> chunks;

    @Data
    public static class ChunkItem {
        @NotBlank(message = "chunk id cannot be blank")
        private String id;

        @NotBlank(message = "content cannot be blank")
        private String content;

        private String chapterPath;

        @NotNull(message = "chunkIndex cannot be null")
        private Integer chunkIndex;

        @NotBlank(message = "chunkType cannot be blank")
        private String chunkType;

        @NotBlank(message = "vectorId cannot be blank")
        private String vectorId;
    }
}
