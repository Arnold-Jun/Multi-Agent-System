package com.xiaohongshu.codewiz.codewizagent.agentcore.agent.controller;

import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.AgentChatRequest;
import com.xiaohongshu.codewiz.codewizagent.agentcore.agent.core.AgentControllerCore;
import io.swagger.annotations.Api;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.GraphStateException;
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
 * create on 2025/3/27 20:21
 */
@RestController
@Api(tags = "Agent控制器")
@RequestMapping("/agent")
@Validated
@ResponseBody
@Slf4j
public class AgentController {

    @Resource
    private AgentControllerCore agentControllerCore;

    @PostMapping("/chat")
    public String chat(@RequestBody AgentChatRequest request) throws GraphStateException {
        log.info("chat request: {}", JSONObject.toJSON(request));
        String requestId = "123123123";
        return agentControllerCore.processStreamReturnStr(request);
    }

    @GetMapping("/testState")
    public String testSaver(@RequestParam(value = "requestId") String requestId) {
        agentControllerCore.viewState(requestId);
        return "OK";
    }
}
