package com.zhouruojun.agentcore.agent.controller;

import com.alibaba.fastjson.JSONObject;
import com.zhouruojun.a2acore.spec.AgentCard;
import com.zhouruojun.agentcore.a2a.A2aClientManager;
import com.zhouruojun.agentcore.a2a.A2aRegister;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * A2A Agent控制器
 * 提供A2A协议的Agent注册和管理功能
 *
 */
@RestController
@RequestMapping("/a2a")
@Validated
@ResponseBody
@Slf4j
public class AgentA2AController {

    @Autowired
    private A2aClientManager a2aClientManager;

    /**
     * 注册A2A Agent
     */
    @PostMapping("/register")
    public AgentCard registerA2AAgent(@RequestBody A2aRegister register) {
        log.info("A2A Agent注册: {}", JSONObject.toJSONString(register));
        return a2aClientManager.register(register);
    }

    /**
     * 获取A2A配置
     */
    @GetMapping("/a2aConfig")
    public AgentCard getA2AConfig(@RequestParam() String agentName) {
        return a2aClientManager.getA2aClient(agentName).getAgentCard();
    }

    /**
     * 列出所有A2A Agent
     */
    @GetMapping("/list")
    public List<AgentCard> listA2AAgents() {
        return a2aClientManager.getAgentCards();
    }
}
