package com.km.dto.response;

import lombok.Data;

@Data
public class SearchResultItemVO {

    private String chunkId;
    private String documentId;
    private String documentName;
    private String chapterPath;
    private String content;
    private Float similarityScore;
    private Float rerankScore;
    private Float bm25Score;
    private Float hybridScore;
    private String chunkType;
}
