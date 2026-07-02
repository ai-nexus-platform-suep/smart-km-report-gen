package com.myenglish.qachat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myenglish.qachat.constant.SessionConstants;
import com.myenglish.qachat.dto.req.SaveMessageReq;
import com.myenglish.qachat.dto.resp.MessageVO;
import com.myenglish.qachat.dto.resp.SessionDetailVO;
import com.myenglish.qachat.entity.Message;
import com.myenglish.qachat.entity.Session;
import com.myenglish.qachat.mapper.MessageMapper;
import com.myenglish.qachat.service.MessageService;
import com.myenglish.qachat.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 消息服务实现类
 * <p>
 * 实现消息的保存与分页查询逻辑，保存消息时会同步更新关联会话的元数据。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;
    private final SessionService sessionService;

    /**
     * 保存一条消息到指定会话
     *
     * @param sessionId 会话 ID
     * @param req       消息请求参数
     * @return 保存成功的消息视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO saveMessage(Long sessionId, SaveMessageReq req) {
        Session session = sessionService.getActiveSession(sessionId);
        LocalDateTime now = LocalDateTime.now();
        int nextSeq = session.getMessageCount() + 1;

        Message message = new Message();
        message.setSessionId(sessionId);
        message.setUserId(SessionConstants.DEFAULT_USER_ID);
        message.setSeq(nextSeq);
        message.setRole(req.getRole());
        message.setContent(req.getContent());
        message.setIntentType(req.getIntentType());
        message.setThinkingSteps(req.getThinkingSteps());
        message.setCitations(req.getCitations());
        message.setGenerateStatus(resolveGenerateStatus(req.getGenerateStatus()));
        message.setTokenUsage(req.getTokenUsage());
        message.setStatus(SessionConstants.STATUS_ACTIVE);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        messageMapper.insert(message);

        sessionService.touchSessionAfterMessage(session, req.getContent(), req.getRole());

        return toVO(message);
    }

    /**
     * 分页查询指定会话的消息列表（按 seq 升序排列）
     *
     * @param sessionId 会话 ID
     * @param page      页码
     * @param size      每页条数
     * @return 会话详情（含 sessionId、标题、消息列表和总数）
     */
    @Override
    public SessionDetailVO listMessages(Long sessionId, int page, int size) {
        Session session = sessionService.getActiveSession(sessionId);

        Page<Message> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getSessionId, sessionId)
                .eq(Message::getStatus, SessionConstants.STATUS_ACTIVE)
                .orderByAsc(Message::getSeq);

        Page<Message> result = messageMapper.selectPage(pageParam, wrapper);

        return SessionDetailVO.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .messages(result.getRecords().stream().map(this::toVO).toList())
                .total(result.getTotal())
                .build();
    }

    /**
     * 解析消息生成状态，空值时默认为已完成
     *
     * @param generateStatus 原始生成状态
     * @return 解析后的生成状态
     */
    private int resolveGenerateStatus(Integer generateStatus) {
        if (generateStatus == null) {
            return SessionConstants.GENERATE_STATUS_COMPLETED;
        }
        return generateStatus;
    }

    /**
     * 将消息实体转换为视图对象
     *
     * @param message 消息实体
     * @return 消息视图对象
     */
    private MessageVO toVO(Message message) {
        return MessageVO.builder()
                .messageId(message.getId())
                .seq(message.getSeq())
                .role(message.getRole())
                .content(message.getContent())
                .intentType(message.getIntentType())
                .thinkingSteps(message.getThinkingSteps())
                .citations(message.getCitations())
                .generateStatus(message.getGenerateStatus())
                .tokenUsage(message.getTokenUsage())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .build();
    }
}
