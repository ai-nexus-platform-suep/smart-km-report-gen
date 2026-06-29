package com.myenglish.qachat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qa_message")
public class Message {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    private Long userId;

    /** 会话内消息序号，从 1 递增 */
    private Integer seq;

    private String role;

    private String content;

    private String intentType;

    private String thinkingSteps;

    private String citations;

    /** 0=生成中 1=已完成 2=失败 */
    private Integer generateStatus;

    private Integer tokenUsage;

    /** 1=正常 0=已删除 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
