package com.km.dto.request;

import lombok.Data;

@Data
public class KnowledgeBasePageQuery {

    private int page = 1;
    private int pageSize = 20;
    private String docType;
    private String keyword;
}
