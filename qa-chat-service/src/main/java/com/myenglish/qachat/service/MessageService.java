package com.myenglish.qachat.service;

import com.myenglish.qachat.dto.req.SaveMessageReq;
import com.myenglish.qachat.dto.resp.MessageVO;
import com.myenglish.qachat.dto.resp.SessionDetailVO;

/**
 * 消息服务接口
 * <p>
 * 提供消息的保存与查询功能。
 * </p>
 */
public interface MessageService {

    /**
     * 保存一条消息到指定会话
     *
     * @param sessionId 会话 ID
     * @param req       消息请求参数
     * @return 保存成功的消息视图对象
     */
    MessageVO saveMessage(Long sessionId, SaveMessageReq req);

    /**
     * 分页查询指定会话的消息列表
     *
     * @param sessionId 会话 ID
     * @param page      页码
     * @param size      每页条数
     * @return 会话详情（含消息列表及总数）
     */
    SessionDetailVO listMessages(Long sessionId, int page, int size);
}
