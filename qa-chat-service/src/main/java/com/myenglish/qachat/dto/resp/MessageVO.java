package com.myenglish.qachat.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageVO {

    private Long messageId;

    private Integer seq;

    private String role;

    private String content;

    private String intentType;

    private String thinkingSteps;

    private String citations;

    private Integer generateStatus;

    private Integer tokenUsage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
