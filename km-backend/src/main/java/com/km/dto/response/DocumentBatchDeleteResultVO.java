package com.km.dto.response;

import java.util.List;

public class DocumentBatchDeleteResultVO {
    private List<String> deletedIds;
    private Integer kbDocCount;

    public List<String> getDeletedIds() { return deletedIds; }
    public void setDeletedIds(List<String> deletedIds) { this.deletedIds = deletedIds; }
    public Integer getKbDocCount() { return kbDocCount; }
    public void setKbDocCount(Integer kbDocCount) { this.kbDocCount = kbDocCount; }
}
