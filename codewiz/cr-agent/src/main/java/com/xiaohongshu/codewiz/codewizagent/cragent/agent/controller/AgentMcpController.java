package com.xiaohongshu.codewiz.codewizagent.cragent.agent.controller;

import com.alibaba.fastjson.JSONObject;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.McpClientManager;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.config.McpConfigManager;
import com.xiaohongshu.codewiz.codewizagent.cragent.mcp.local.LoadMcpRequest;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import io.swagger.annotations.Api;
import jakarta.annotation.Resource;
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
 * create on 2025/3/31 23:22
 */
@RestController
@Api(tags = "Agent控制器")
@RequestMapping("/agentmcp")
@Validated
@ResponseBody
@Slf4j
public class AgentMcpController {

    @Resource
    private McpClientManager mcpClientManager;

    @PostMapping("/loadMcp")
    public String loadMcpClient(@RequestBody LoadMcpRequest loadMcpRequest) {
        JSONObject result = new JSONObject();
        try {
            boolean data = mcpClientManager.loadMcpClient(loadMcpRequest);
            result.put("success", data);
            result.put("code", 0);
            result.put("msg", "加载成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("code", 500);
            result.put("msg", "加载失败，" + e.getMessage());
        }
        return result.toJSONString();
    }

    @PostMapping("/testLoadMcp")
    public String testLoadMcpClient(@RequestBody JSONObject jsonObject) {
        ToolExecutionRequest callToolRequest = ToolExecutionRequest.builder()
                .name(jsonObject.getString("name"))
                .arguments(jsonObject.getString("arguments"))
                .id(jsonObject.getString("id"))
                .build();
//        McpSchema.CallToolRequest callToolRequest = new McpSchema.CallToolRequest(jsonObject.getString("name"), jsonObject.getString("arguments"));
        return mcpClientManager.runClientMethod(callToolRequest);
    }

    @GetMapping("/getLoadMcp")
    public LoadMcpRequest getLoadMcp(@RequestParam(name = "name") String name) {
        return McpConfigManager.getMcpConfig(name);
    }
}
