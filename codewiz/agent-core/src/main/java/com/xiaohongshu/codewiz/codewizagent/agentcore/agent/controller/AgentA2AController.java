package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.controller;

import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.a2acore.spec.AgentCard;
import com.xiaohongshu.codewiz.codewizagent.agentcore.a2a.A2aClientManager;
import com.xiaohongshu.codewiz.codewizagent.agentcore.a2a.A2aRegister;
import io.swagger.annotations.Api;
import java.util.List;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *
 * </p>
 *
 * @author 瑞诺
 * create on 2025/5/26 19:57
 */
@RestController
@Api(tags = "A2aAgent控制器")
@RequestMapping("/a2a")
@Validated
@ResponseBody
@Slf4j
public class AgentA2AController {

    @Resource
    private A2aClientManager a2aClientManager;

    @PostMapping("/register")
    public AgentCard registerA2AAgent(@RequestBody A2aRegister register) {
        log.info("A2A Agent注册: {}", JSONObject.toJSONString(register));
        return a2aClientManager.register(register);
    }

    @GetMapping("/a2aConfig")
    public AgentCard getA2AConfig(@RequestParam() String agentName) {
        return a2aClientManager.getA2aClient(agentName).getAgentCard();
    }

    @GetMapping("/list")
    public List<AgentCard> listA2AAgents() {
        return a2aClientManager.getAgentCards();
    }
}
