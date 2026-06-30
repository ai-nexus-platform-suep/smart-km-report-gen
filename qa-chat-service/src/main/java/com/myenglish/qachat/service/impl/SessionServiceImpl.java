package com.myenglish.qachat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myenglish.qachat.constant.SessionConstants;
import com.myenglish.qachat.dto.req.CreateSessionReq;
import com.myenglish.qachat.dto.resp.SessionVO;
import com.myenglish.qachat.entity.Message;
import com.myenglish.qachat.entity.Session;
import com.myenglish.qachat.exception.BusinessException;
import com.myenglish.qachat.mapper.MessageMapper;
import com.myenglish.qachat.mapper.SessionMapper;
import com.myenglish.qachat.service.SessionService;
import com.myenglish.qacommon.dto.ApiCode;
import com.myenglish.qacommon.dto.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 会话服务实现类
 */
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final SessionMapper sessionMapper;
    private final MessageMapper messageMapper;

    /**
     * 创建新会话
     * @param req 创建会话请求参数
     * @return 创建成功的会话视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SessionVO createSession(CreateSessionReq req) {
        LocalDateTime now = LocalDateTime.now();
        Session session = new Session();
        session.setUserId(SessionConstants.DEFAULT_USER_ID);
        session.setTitle(resolveTitle(req.getTitle()));
        session.setStatus(SessionConstants.STATUS_ACTIVE);
        session.setMessageCount(0);
        session.setLastMessageAt(now);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);

        return toVO(session);
    }

    /**
     * 分页查询活跃会话列表，按最后消息时间及 ID 降序排列
     *
     * @param page 页码
     * @param size 每页条数
     * @return 会话分页结果
     */
    @Override
    public PageResult<SessionVO> listSessions(int page, int size) {
        Page<Session> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Session> wrapper = new LambdaQueryWrapper<Session>()
                .eq(Session::getStatus, SessionConstants.STATUS_ACTIVE)
                .orderByDesc(Session::getLastMessageAt)
                .orderByDesc(Session::getId);

        Page<Session> result = sessionMapper.selectPage(pageParam, wrapper);

        PageResult<SessionVO> pageResult = new PageResult<>();
        pageResult.setItems(result.getRecords().stream().map(this::toVO).toList());
        pageResult.setTotal(result.getTotal());
        pageResult.setPage(page);
        pageResult.setSize(size);
        return pageResult;
    }

    /**
     * 获取活跃会话
     *
     * @param sessionId 会话 ID
     * @return 活跃的会话实体
     * @throws BusinessException 当会话不存在或已删除时抛出
     */
    @Override
    public Session getActiveSession(Long sessionId) {
        Session session = sessionMapper.selectById(sessionId);
        if (session == null || session.getStatus() == SessionConstants.STATUS_DELETED) {
            throw new BusinessException(ApiCode.DATA_NOT_FOUND, "会话不存在");
        }
        return session;
    }

    /**
     * 软删除指定会话及其关联的所有消息
     *
     * @param sessionId 会话 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId) {
        getActiveSession(sessionId);

        LocalDateTime now = LocalDateTime.now();
        sessionMapper.update(null, new LambdaUpdateWrapper<Session>()
                .eq(Session::getId, sessionId)
                .eq(Session::getStatus, SessionConstants.STATUS_ACTIVE)
                .set(Session::getStatus, SessionConstants.STATUS_DELETED)
                .set(Session::getDeletedAt, now)
                .set(Session::getUpdatedAt, now));

        messageMapper.update(null, new LambdaUpdateWrapper<Message>()
                .eq(Message::getSessionId, sessionId)
                .eq(Message::getStatus, SessionConstants.STATUS_ACTIVE)
                .set(Message::getStatus, SessionConstants.STATUS_DELETED)
                .set(Message::getDeletedAt, now)
                .set(Message::getUpdatedAt, now));
    }

    /**
     * 消息发送后更新会话元数据
     *
     * @param session     当前会话实体
     * @param userContent 消息内容
     * @param role        消息角色（user / assistant）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void touchSessionAfterMessage(Session session, String userContent, String role) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<Session> updateWrapper = new LambdaUpdateWrapper<Session>()
                .eq(Session::getId, session.getId())
                .set(Session::getMessageCount, session.getMessageCount() + 1)
                .set(Session::getLastMessageAt, now)
                .set(Session::getUpdatedAt, now);

        if (SessionConstants.ROLE_USER.equals(role)
                && SessionConstants.DEFAULT_TITLE.equals(session.getTitle())
                && StringUtils.hasText(userContent)) {
            updateWrapper.set(Session::getTitle, buildTitleFromContent(userContent));
        }

        sessionMapper.update(null, updateWrapper);
    }

    /**
     * 从消息内容中截取会话标题
     *
     * @param content 消息内容
     * @return 截取后的标题，超长时尾部加 "..."
     */
    private String buildTitleFromContent(String content) {
        String trimmed = content.trim();
        if (trimmed.length() <= SessionConstants.TITLE_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, SessionConstants.TITLE_MAX_LENGTH) + "...";
    }

    /**
     * 解析会话标题，为空时使用默认标题
     *
     * @param title 原始标题
     * @return 解析后的标题
     */
    private String resolveTitle(String title) {
        return StringUtils.hasText(title) ? title.trim() : SessionConstants.DEFAULT_TITLE;
    }

    /**
     * 将会话实体转换为视图对象
     *
     * @param session 会话实体
     * @return 会话视图对象
     */
    private SessionVO toVO(Session session) {
        return SessionVO.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .messageCount(session.getMessageCount())
                .lastMessageAt(session.getLastMessageAt())
                .createdAt(session.getCreatedAt())
                .build();
    }
}
