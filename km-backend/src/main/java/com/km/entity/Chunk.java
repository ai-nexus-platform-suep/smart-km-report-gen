package com.km.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档切片实体，对应表 chunk
 */
@Data
public class Chunk {

    private String id;
    private String docId;
    private String content;
    private String chapterPath;
    private Integer chunkIndex;
    private String chunkType;
    private String vectorId;
    private LocalDateTime createdAt;
}
