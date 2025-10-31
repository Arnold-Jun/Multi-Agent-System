package com.zhouruojun.travelingagent.agent.controller;

import com.zhouruojun.travelingagent.agent.dto.TravelIntentForm;
import com.zhouruojun.travelingagent.agent.dto.AgentChatRequest;

import java.util.LinkedHashMap;

/**
 * Traveling Agent WebSocket Service 接口
 * 定义WebSocket消息处理的服务方法
 */
public interface TravelingWsService {

    /**
     * 处理聊天消息（从LinkedHashMap）
     * @param payload 消息负载
     * @param userEmail 用户邮箱
     */
    void chat(LinkedHashMap<String, Object> payload, String userEmail);

    /**
     * 处理聊天消息（从AgentChatRequest）
     * @param request 聊天请求
     * @param userEmail 用户邮箱
     */
    void chat(AgentChatRequest request, String userEmail);

    /**
     * 处理用户输入（从userInput节点恢复）
     * @param payload 消息负载
     * @param userEmail 用户邮箱
     */
    void humanInput(LinkedHashMap<String, Object> payload, String userEmail);

    /**
     * 处理用户输入（从userInput节点恢复）
     * @param request 聊天请求
     * @param userEmail 用户邮箱
     */
    void humanInput(AgentChatRequest request, String userEmail);

    /**
     * 处理表单提交
     * @param form 表单数据
     * @param userEmail 用户邮箱
     */
    void submitForm(TravelIntentForm form, String userEmail);

    /**
     * Ping测试
     * @return Pong
     */
    String ping();
}

