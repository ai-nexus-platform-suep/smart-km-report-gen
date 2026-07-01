package com.km.dto.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentProcessMessage {

    private String jobId;
    private String documentId;
    private String kbId;
    private String kbName;
    private String rawObject;
    private String filename;
    private String mimeType;
    private String parserBackend;
    private String chunkStrategy;
    private Integer attempt;
    private String callbackUrl;
    private LocalDateTime createdAt;
}
