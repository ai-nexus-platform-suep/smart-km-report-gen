package com.km.vo;

import lombok.Data;

/**
 * 切片视图对象，返回给前端
 */
@Data
public class ChunkVO {

    private String id;
    private String docId;
    private String content;
    private String chapterPath;
    private Integer chunkIndex;
    private String chunkType;
    private Integer charCount;
}
