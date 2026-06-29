package com.myenglish.qachat.service;

import com.myenglish.qachat.dto.req.CreateSessionReq;
import com.myenglish.qachat.dto.resp.SessionVO;
import com.myenglish.qachat.entity.Session;
import com.myenglish.qacommon.dto.PageResult;

/**
 * 会话服务接口
 * <p>
 * 提供会话的创建、查询、删除以及消息发送后会话状态更新等功能。
 * </p>
 */
public interface SessionService {

    /**
     * 创建新会话
     *
     * @param req 创建会话请求参数
     * @return 创建成功的会话视图对象
     */
    SessionVO createSession(CreateSessionReq req);

    /**
     * 分页查询会话列表
     *
     * @param page 页码
     * @param size 每页条数
     * @return 会话分页结果
     */
    PageResult<SessionVO> listSessions(int page, int size);

    /**
     * 获取活跃会话（不存在或已删除会抛出异常）
     *
     * @param sessionId 会话 ID
     * @return 活跃的会话实体
     * @throws com.myenglish.qachat.exception.BusinessException 当会话不存在或已删除时抛出
     */
    Session getActiveSession(Long sessionId);

    /**
     * 软删除指定会话及其关联消息
     *
     * @param sessionId 会话 ID
     */
    void deleteSession(Long sessionId);

    /**
     * 消息发送后更新会话元数据
     *
     * @param session     当前会话实体
     * @param userContent 消息内容
     * @param role        消息角色（user / assistant）
     */
    void touchSessionAfterMessage(Session session, String userContent, String role);
}
