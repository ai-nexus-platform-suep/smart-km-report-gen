package com.km.dto.response;

public class DocumentDeleteResultVO {
    private String deletedDocumentId;
    private Integer kbDocCount;

    public String getDeletedDocumentId() { return deletedDocumentId; }
    public void setDeletedDocumentId(String deletedDocumentId) { this.deletedDocumentId = deletedDocumentId; }
    public Integer getKbDocCount() { return kbDocCount; }
    public void setKbDocCount(Integer kbDocCount) { this.kbDocCount = kbDocCount; }
}
