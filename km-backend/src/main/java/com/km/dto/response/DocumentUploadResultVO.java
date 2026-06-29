package com.km.dto.response;

public class DocumentUploadResultVO {
    private DocumentVO document;
    private Integer kbDocCount;

    public DocumentVO getDocument() { return document; }
    public void setDocument(DocumentVO document) { this.document = document; }
    public Integer getKbDocCount() { return kbDocCount; }
    public void setKbDocCount(Integer kbDocCount) { this.kbDocCount = kbDocCount; }
}
