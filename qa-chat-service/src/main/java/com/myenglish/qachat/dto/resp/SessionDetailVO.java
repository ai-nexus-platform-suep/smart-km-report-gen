package com.myenglish.qachat.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SessionDetailVO {

    private Long sessionId;

    private String title;

    private List<MessageVO> messages;

    private long total;
}
