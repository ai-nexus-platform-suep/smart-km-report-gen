package com.km.dto.request;

import java.util.List;
import java.util.Map;

public class DocumentStatusCallbackRequest {
    private String documentId;
    private String status;
    private String errorMsg;
    private List<Map<String, Object>> chunks;

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public List<Map<String, Object>> getChunks() { return chunks; }
    public void setChunks(List<Map<String, Object>> chunks) { this.chunks = chunks; }
}
