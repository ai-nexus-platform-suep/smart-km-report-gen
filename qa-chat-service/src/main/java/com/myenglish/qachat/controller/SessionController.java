package com.myenglish.qachat.controller;

import com.myenglish.qachat.dto.req.CreateSessionReq;
import com.myenglish.qachat.dto.req.SaveMessageReq;
import com.myenglish.qachat.dto.resp.MessageVO;
import com.myenglish.qachat.dto.resp.SessionDetailVO;
import com.myenglish.qachat.dto.resp.SessionVO;
import com.myenglish.qachat.service.MessageService;
import com.myenglish.qachat.service.SessionService;
import com.myenglish.qacommon.dto.ApiResponse;
import com.myenglish.qacommon.dto.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话管理控制器
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final MessageService messageService;

    /**
     * 创建新会话
     *
     * @param req 创建会话请求参数，可选；为 null 时使用默认值
     * @return 创建成功的会话信息
     */
    @PostMapping
    public ApiResponse<SessionVO> createSession(@RequestBody(required = false) @Valid CreateSessionReq req) {
        CreateSessionReq request = req != null ? req : new CreateSessionReq();
        return ApiResponse.success(sessionService.createSession(request));
    }

    /**
     * 分页查询会话列表
     *
     * @param page 页码，默认第 1 页
     * @param size 每页条数，默认 20 条
     * @return 会话分页结果
     */
    @GetMapping
    public ApiResponse<PageResult<SessionVO>> listSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(sessionService.listSessions(page, size));
    }

    /**
     * 分页查询指定会话的消息列表
     *
     * @param sessionId 会话 ID
     * @param page      页码，默认第 1 页
     * @param size      每页条数，默认 50 条
     * @return 会话详情（含消息列表）
     */
    @GetMapping("/{sessionId}/messages")
    public ApiResponse<SessionDetailVO> listMessages(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(messageService.listMessages(sessionId, page, size));
    }

    /**
     * 向指定会话保存一条消息
     *
     * @param sessionId 会话 ID
     * @param req       消息请求参数
     * @return 保存成功的消息信息
     */
    @PostMapping("/{sessionId}/messages")
    public ApiResponse<MessageVO> saveMessage(
            @PathVariable Long sessionId,
            @RequestBody @Valid SaveMessageReq req) {
        return ApiResponse.success(messageService.saveMessage(sessionId, req));
    }

    /**
     * 删除指定会话（软删除，同时删除关联消息）
     *
     * @param sessionId 会话 ID
     * @return 空响应
     */
    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable Long sessionId) {
        sessionService.deleteSession(sessionId);
        return ApiResponse.success();
    }
}
