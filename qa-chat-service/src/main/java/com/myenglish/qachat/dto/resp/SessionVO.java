package com.myenglish.qachat.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionVO {

    private Long sessionId;

    private String title;

    private Integer messageCount;

    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;
}
